"""Build-source verification and asset upload for an existing private draft only."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise RuntimeError("Unexpected redirect from the GitHub release endpoint")


def request_json(url, data=None, content_type=None):
    headers = {
        "Accept": "application/vnd.github+json",
        "Authorization": "Bearer " + os.environ["GH_TOKEN"],
        "X-GitHub-Api-Version": "2022-11-28",
        "User-Agent": "DuoFrost-draft-release-upload",
    }
    if content_type:
        headers["Content-Type"] = content_type
    request = urllib.request.Request(url, data=data, headers=headers)
    try:
        with urllib.request.build_opener(NoRedirect()).open(request, timeout=180) as response:
            return json.load(response)
    except urllib.error.HTTPError as error:
        try:
            message = json.load(error).get("message", "Request rejected")
        except (ValueError, AttributeError):
            message = "Request rejected"
        raise RuntimeError(f"GitHub request failed ({error.code}): {message}") from None


def read_draft(repo, release_id):
    release = request_json(f"https://api.github.com/repos/{repo}/releases/{release_id}")
    if release.get("id") != int(release_id):
        raise RuntimeError("Unexpected release ID")
    if not release.get("draft") or not release.get("prerelease") or release.get("published_at"):
        raise RuntimeError("Uploads require an unpublished draft pre-release")
    if not re.fullmatch(r"v[0-9A-Za-z][0-9A-Za-z._-]*", release["tag_name"]):
        raise RuntimeError("Release tag is not safe for artifact filenames")
    if not re.fullmatch(r"[0-9a-f]{40}", release["target_commitish"]):
        raise RuntimeError("Draft must target an exact application commit")
    upload_url = release["upload_url"].split("{", 1)[0]
    parsed = urllib.parse.urlsplit(upload_url)
    expected_path = f"/repos/{repo}/releases/{release_id}/assets"
    if (parsed.scheme, parsed.netloc, parsed.path, parsed.query, parsed.fragment) != (
        "https", "uploads.github.com", expected_path, "", ""
    ):
        raise RuntimeError("Unexpected GitHub asset-upload URL")
    return release


def check_identity(release, state):
    if release["tag_name"] != state["tag"] or release["target_commitish"] != state["source_sha"]:
        raise RuntimeError("Draft identity changed while building")


def prepare(state_path, release_id):
    repo = os.environ["GITHUB_REPOSITORY"]
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repo):
        raise RuntimeError("Invalid repository identity")
    if not re.fullmatch(r"[1-9][0-9]{0,19}", release_id):
        raise RuntimeError("A numeric existing release ID is required")
    release = read_draft(repo, release_id)
    state = {
        "repo": repo,
        "release_id": release["id"],
        "tag": release["tag_name"],
        "source_sha": release["target_commitish"],
    }
    state_path.write_text(json.dumps(state, indent=2) + "\n")
    output = os.environ.get("GITHUB_OUTPUT")
    if output:
        with open(output, "a") as stream:
            stream.write(f"source_sha={state['source_sha']}\n")
    print(f"Validated draft {state['release_id']} at {state['source_sha']}")


def package(state_path):
    state = json.loads(state_path.read_text())
    head = subprocess.check_output(["git", "rev-parse", "HEAD"], text=True).strip()
    if head != state["source_sha"]:
        raise RuntimeError("Checkout differs from the draft's application commit")
    subprocess.run(["git", "diff", "HEAD", "--exit-code"], check=True)
    counts = {key: 0 for key in ("tests", "failures", "errors", "skipped")}
    reports = list(Path("app/build/test-results/testDebugUnitTest").glob("TEST-*.xml"))
    for report in reports:
        suite = ET.parse(report).getroot()
        for key in counts:
            counts[key] += int(suite.get(key, "0"))
    if not reports or counts["tests"] <= counts["skipped"] or counts["failures"] or counts["errors"]:
        raise RuntimeError(f"JVM regression tests did not pass: {counts}")
    print("JVM test results:", counts)
    metadata = json.loads(Path("app/build/outputs/apk/debug/output-metadata.json").read_text())
    if metadata["applicationId"] != "io.github.tufein.duofrost.debug":
        raise RuntimeError("Only the separate debug application may be uploaded")
    apk = Path("app/build/outputs/apk/debug/app-debug.apk")
    apksigner = Path(os.environ["ANDROID_HOME"]) / "build-tools/35.0.0/apksigner"
    subprocess.run([str(apksigner), "verify", str(apk)], check=True)
    prefix = "DuoFrost-" + state["tag"][1:]
    assets_dir = state_path.parent / "draft-assets"
    assets_dir.mkdir()
    apk_name = prefix + "-debug.apk"
    source_name = prefix + "-source.zip"
    shutil.copy2(apk, assets_dir / apk_name)
    subprocess.run([
        "git", "archive", "--format=zip", f"--prefix={prefix}/",
        "--output=" + str(assets_dir / source_name), state["source_sha"],
    ], check=True)
    checksums = "".join(
        hashlib.sha256((assets_dir / name).read_bytes()).hexdigest() + "  " + name + "\n"
        for name in (apk_name, source_name)
    )
    (assets_dir / "SHA256SUMS").write_text(checksums)
    state["assets"] = [
        {"name": name, "size": (assets_dir / name).stat().st_size,
         "sha256": hashlib.sha256((assets_dir / name).read_bytes()).hexdigest()}
        for name in (apk_name, source_name, "SHA256SUMS")
    ]
    state["test_results"] = counts
    state["version_name"] = metadata["elements"][0]["versionName"]
    state_path.write_text(json.dumps(state, indent=2) + "\n")


def verify_asset(asset, expected):
    if asset.get("state") != "uploaded" or asset.get("size") != expected["size"]:
        raise RuntimeError("Asset upload is incomplete: " + expected["name"])
    if asset.get("digest") != "sha256:" + expected["sha256"]:
        raise RuntimeError("Remote asset digest differs: " + expected["name"])


def upload(state_path):
    state = json.loads(state_path.read_text())
    for expected in state["assets"]:
        release = read_draft(state["repo"], state["release_id"])
        check_identity(release, state)
        matches = [asset for asset in release["assets"] if asset["name"] == expected["name"]]
        if matches:
            verify_asset(matches[0], expected)
            continue
        url = release["upload_url"].split("{", 1)[0] + "?" + urllib.parse.urlencode({"name": expected["name"]})
        asset = request_json(url, data=(state_path.parent / "draft-assets" / expected["name"]).read_bytes(),
                             content_type="application/octet-stream")
        verify_asset(asset, expected)
        print("Uploaded and verified:", expected["name"])
    release = read_draft(state["repo"], state["release_id"])
    check_identity(release, state)
    for expected in state["assets"]:
        matches = [asset for asset in release["assets"] if asset["name"] == expected["name"]]
        if len(matches) != 1:
            raise RuntimeError("Unexpected final asset list")
        verify_asset(matches[0], expected)
    print("Draft/pre-release retained; all three asset digests verified")
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary:
        with open(summary, "a") as stream:
            stream.write(f"Verified three private draft assets for release {state['release_id']}.\n")
            stream.write(f"Application source: `{state['source_sha']}`.\n")
            stream.write(f"JVM tests: {state['test_results']}.\n")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("operation", choices=("prepare", "package", "upload"))
    parser.add_argument("--state", type=Path, required=True)
    parser.add_argument("--release-id")
    args = parser.parse_args()
    if args.operation == "prepare":
        if args.release_id is None:
            parser.error("--release-id is required for prepare")
        prepare(args.state, args.release_id)
    elif args.operation == "package":
        package(args.state)
    else:
        upload(args.state)


if __name__ == "__main__":
    main()
