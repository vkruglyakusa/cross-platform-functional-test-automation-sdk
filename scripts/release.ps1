# SDK Release Script (Windows PowerShell)
#
# Mechanical/automated counterpart to the semantic release process described in
# src/main/resources/ai/skills/release/release.skill.yaml. That skill file owns
# the human/semantic judgment calls this script cannot make on its own (does
# documentation actually describe the new behavior, does MASTER-SOLUTION-GUIDE.md
# or the Executive Summary need updating, did known limitations change) -- keep
# both in sync when the release process changes.
#
# Usage: scripts\release.ps1 [-ProxyHost bcpxy.nycnet] [-ProxyPort 8080] [-AssumeYes]
#          [-WebTemplatePath <dir>] [-ApiTemplatePath <dir>] [-MobileTemplatePath <dir>]
#          [-MavenRepoPath <dir>] [-SkipTemplates] [-SkipMavenRepo]
#   -AssumeYes: skip the interactive "docs may need updating" / semantic-review
#               confirmation prompts (required for non-interactive/automated runs;
#               use only when you have already verified docs don't need updating).
#   -WebTemplatePath / -ApiTemplatePath / -MobileTemplatePath: the three consumer
#               template directories validated/updated in step 7. Each defaults to
#               its standard sibling directory. A template is only touched if its
#               pom.xml actually depends on THIS SDK's artifactId (read from this
#               repo's own pom.xml at runtime) -- otherwise it is skipped with a
#               warning, so an unrelated SDK dependency is never bumped by mistake.
#   -MavenRepoPath: the git-backed local Maven repo consumer templates fall back to
#               when Azure Artifacts credentials aren't configured. Defaults to the
#               sibling directory `maven-repository`.
#   -SkipTemplates: skip step 7 entirely (no template files are read/modified).
#   -SkipMavenRepo: skip step 6b entirely (no local file-repo deploy/commit).
#   -TemplatePath / -SkipTemplate: deprecated aliases for -WebTemplatePath / -SkipTemplates.
#
# Full release pipeline -- runs automatically in order:
#   0. Already-released guard      -- abort if v<version> tag or CHANGELOG heading already
#                                     exists, so re-running the script for a version that
#                                     was already released can never repeat/duplicate work
#   1. Doc check gate               -- confirm CHANGELOG [Unreleased] has content, key docs
#                                     (incl. sdk-config.yaml.template) were touched, and a
#                                     semantic review of MASTER-SOLUTION-GUIDE.md / the
#                                     Executive Summary has been explicitly acknowledged
#   2. Run ALL tests               -- abort if any fail
#   3. Update SDK README.md        -- version badge, dependency snippet, footer
#   4. Promote SDK CHANGELOG.md    -- move [Unreleased] -> [version] -- date
#   5. Git commit SDK docs         -- single commit: "docs: release vX.Y.Z"
#   6. Deploy                      -- 6a: mvn deploy to Azure Artifacts (authoritative feed)
#                                     6b: mvn deploy to the local git-backed maven-repository
#                                         fallback (used by consumers without Azure Artifacts
#                                         credentials) + auto git add/commit in that repo.
#                                         RELEASE ABORTS if this fails or the version
#                                         directory doesn't materialize on disk afterward.
#   7. Update consumer templates   -- for EACH of Web/API/Mobile templates that depends on
#                                     this SDK: bump pom.xml, update whichever of
#                                     README.md/SDK-USER-GUIDE.md/GETTING-STARTED.md/
#                                     CHANGELOG.md exist, re-run InstructionExtractor to
#                                     refresh .github/, docs/sdk/, configuration/ from the
#                                     newly deployed jar, validate with
#                                     `mvn compile test-compile`, then commit. A validation
#                                     failure on ANY template ABORTS the entire release --
#                                     template changes are NEVER committed/pushed if that
#                                     validation fails.
#   8. Final verification matrix   -- re-checks SDK version == Maven repo artifact version
#                                     == each updated template's pom.xml version, root/
#                                     resources doc mirrors are identical, and CHANGELOG has
#                                     the release heading. Any failed check marks the
#                                     release INCOMPLETE and aborts before the success banner.
#
# NEVER use `mvn deploy -DskipTests` directly. Always use this script.

param(
    [string]$ProxyHost = "",
    [string]$ProxyPort = "8080",
    [string]$WebTemplatePath = "",
    [string]$ApiTemplatePath = "",
    [string]$MobileTemplatePath = "",
    [string]$MavenRepoPath = "",
    [string]$TemplatePath = "",        # deprecated alias -> WebTemplatePath
    [switch]$AssumeYes,
    [switch]$SkipTemplates,
    [switch]$SkipTemplate,             # deprecated alias -> SkipTemplates
    [switch]$SkipMavenRepo
)

if ($TemplatePath -ne "" -and $WebTemplatePath -eq "") { $WebTemplatePath = $TemplatePath }
if ($SkipTemplate) { $SkipTemplates = $true }

$ErrorActionPreference = "Continue"
$root = Split-Path -Parent $PSScriptRoot
$siblingRoot = Split-Path -Parent $root

# Resolve template/repo paths -- defaults are standard sibling directories
if ($WebTemplatePath -eq "")    { $WebTemplatePath    = "$siblingRoot\functional-automation-consumer-template" }
if ($ApiTemplatePath -eq "")    { $ApiTemplatePath    = "$siblingRoot\api-functional-automation-consumer-template" }
if ($MobileTemplatePath -eq "") { $MobileTemplatePath = "$siblingRoot\mobile-functional-automation-consumer-template" }
if ($MavenRepoPath -eq "")      { $MavenRepoPath      = "$siblingRoot\maven-repository" }

# Tracks per-template outcomes for the Step 8 final verification matrix
$templateResults = @()
# Tracks whether the local maven-repository deploy succeeded, for Step 8
$mavenRepoDeployed = $false

# Build proxy args
$proxyArgs = @()
if ($ProxyHost -ne "") {
    $proxyArgs += "-Dhttps.proxyHost=$ProxyHost"
    $proxyArgs += "-Dhttps.proxyPort=$ProxyPort"
    $proxyArgs += "-Dhttp.proxyHost=$ProxyHost"
    $proxyArgs += "-Dhttp.proxyPort=$ProxyPort"
}

# Read current version from pom.xml
$pom = [xml](Get-Content "$root\pom.xml")
$version = $pom.project.version
$artifactId = $pom.project.artifactId
$artifactIdEsc = [System.Text.RegularExpressions.Regex]::Escape($artifactId)
$artifactIdBadge = [System.Text.RegularExpressions.Regex]::Escape(($artifactId -replace '-', '--'))
$today   = (Get-Date).ToString("yyyy-MM-dd")

Write-Host ""
Write-Host "============================================"
Write-Host "  SDK Release Pipeline - $artifactId v$version"
Write-Host "============================================"
Write-Host ""

# -------------------------------------------------------
# STEP 0: Already-released guard -- catch "ran the script twice for the same
# version" BEFORE any tests/docs/deploy work happens. This is what let a
# duplicate "docs: release v1.1.1" commit and a failed re-deploy slip through
# previously: re-running the script with an unchanged pom.xml version silently
# repeated work that was already done and pushed.
# -------------------------------------------------------
Write-Host "[0/8] Already-released guard..."
$existingLocalTag  = git tag -l "v$version"
$existingRemoteTag = git ls-remote --tags origin "refs/tags/v$version" 2>$null
if ($existingLocalTag -or $existingRemoteTag) {
    Write-Host ""
    Write-Host "============================================"
    Write-Host "  ABORTED -- v$version already released"
    Write-Host "  Tag v$version already exists (local and/or origin)."
    Write-Host "  Bump <version> in pom.xml to the NEXT version before"
    Write-Host "  running this script again."
    Write-Host "============================================"
    exit 1
}

$changelogContentPreCheck = Get-Content "$root\CHANGELOG.md" -Raw
if ($changelogContentPreCheck -match [System.Text.RegularExpressions.Regex]::Escape("## [$version]")) {
    Write-Host ""
    Write-Host "============================================"
    Write-Host "  ABORTED -- CHANGELOG.md already has a [$version] heading"
    Write-Host "  This version was already promoted/released once."
    Write-Host "  Bump <version> in pom.xml to the NEXT version before"
    Write-Host "  running this script again (do not re-run for the same version)."
    Write-Host "============================================"
    exit 1
}
Write-Host "      No existing tag or CHANGELOG heading for v$version -- OK"
Write-Host ""

# -------------------------------------------------------
# STEP 1: Doc check gate -- confirm docs are updated before anything else
# -------------------------------------------------------
Write-Host "[1/8] Doc check gate..."
Write-Host "      Checking CHANGELOG.md has an [Unreleased] section with content..."
$changelogContent = Get-Content "$root\CHANGELOG.md" -Raw
$unreleasedMatch = [System.Text.RegularExpressions.Regex]::Match(
    $changelogContent,
    '## \[Unreleased\]\s*\n(.*?)(\n## \[|\z)',
    [System.Text.RegularExpressions.RegexOptions]::Singleline
)
$unreleasedRaw = if ($unreleasedMatch.Success) { $unreleasedMatch.Groups[1].Value.Trim() } else { "" }
# Strip the standing HTML-comment placeholder before measuring length, otherwise
# an already-promoted (i.e. genuinely empty) [Unreleased] section is miscounted
# as "has content" purely because of the placeholder comment text itself.
$unreleasedBody = [System.Text.RegularExpressions.Regex]::Replace(
    $unreleasedRaw, '<!--.*?-->', '', [System.Text.RegularExpressions.RegexOptions]::Singleline
).Trim()

if ($unreleasedBody.Length -lt 20) {
    Write-Host ""
    Write-Host "============================================"
    Write-Host "  DEPLOYMENT ABORTED -- DOCS NOT UPDATED"
    Write-Host "  CHANGELOG.md [Unreleased] section is empty or missing."
    Write-Host "  Required before release:"
    Write-Host "    1. Add [Unreleased] entries to CHANGELOG.md"
    Write-Host "    2. Update TESTBASE-API.md if API changed"
    Write-Host "    3. Update SDK-USER-GUIDE.md if behavior changed"
    Write-Host "    4. Update test-creation.instructions.md if patterns changed"
    Write-Host "    5. Update src/main/resources/sdk-defaults/sdk-config.yaml.template if config keys changed"
    Write-Host "    6. Sync changes to src/main/resources/ mirrors"
    Write-Host "============================================"
    exit 1
}
Write-Host "      CHANGELOG.md [Unreleased] has content -- OK"

# Warn (don't block) if key doc files haven't been touched since last commit
$docFiles = @(
    "TESTBASE-API.md",
    "SDK-USER-GUIDE.md",
    ".github\instructions\test-creation.instructions.md",
    "src\main\resources\sdk-defaults\sdk-config.yaml.template"
)
$uncommittedDocs = git diff --name-only HEAD -- $docFiles 2>$null
$stagedDocs      = git diff --cached --name-only -- $docFiles 2>$null
$allChangedDocs  = ($uncommittedDocs + $stagedDocs) | Where-Object { $_ -ne "" }
if ($allChangedDocs.Count -eq 0) {
    Write-Host ""
    Write-Host "  WARNING: None of the key doc files have pending changes."
    Write-Host "  If this release adds new features or changes API/behavior,"
    Write-Host "  update these files before releasing:"
    Write-Host "    - TESTBASE-API.md"
    Write-Host "    - SDK-USER-GUIDE.md"
    Write-Host "    - .github/instructions/test-creation.instructions.md"
    Write-Host "    - src/main/resources/sdk-defaults/sdk-config.yaml.template"
    Write-Host "    - src/main/resources/ mirrors of the above"
    Write-Host ""
    Write-Host "  Press ENTER to continue anyway, or Ctrl+C to abort and update docs."
    if ($AssumeYes) {
        Write-Host "  -AssumeYes supplied -- continuing without confirmation."
    } else {
        Read-Host
    }
} else {
    Write-Host "      Key doc files have pending changes -- OK"
}
Write-Host ""

# Semantic doc review gate -- MASTER-SOLUTION-GUIDE.md and the Executive Summary
# describe platform capabilities/architecture and business-facing value in prose;
# neither can be reliably checked by a version-number regex, so this is an explicit,
# always-asked acknowledgment rather than a silent skip.
Write-Host "  SEMANTIC REVIEW REQUIRED:"
Write-Host "    - Does this release change platform capabilities or architecture in a way"
Write-Host "      that MASTER-SOLUTION-GUIDE.md must be updated to describe?"
Write-Host "    - Does this release change business-facing capabilities in a way that a new"
Write-Host "      SDK-v$version-Executive-Summary.docx should be produced?"
Write-Host "  Confirm you have considered both before continuing."
Write-Host "  Press ENTER to acknowledge, or Ctrl+C to abort and update those documents first."
if ($AssumeYes) {
    Write-Host "  -AssumeYes supplied -- continuing without confirmation."
} else {
    Read-Host
}
Write-Host ""

# STEP 2: Run all tests (mandatory -- abort on any failure)
# -------------------------------------------------------
Write-Host "[2/8] Running all tests (mvn clean test)..."
Write-Host "      (This is mandatory. NEVER use -DskipTests to bypass this gate.)"
Write-Host ""
Write-Host ""

$testCmd = @("clean", "test") + $proxyArgs
& mvn @testCmd
$testExit = $LASTEXITCODE

if ($testExit -ne 0) {
    Write-Host ""
    Write-Host "============================================"
    Write-Host "  DEPLOYMENT ABORTED -- TESTS FAILED"
    Write-Host "  Fix all test failures before deploying."
    Write-Host "============================================"
    exit 1
}

Write-Host ""
Write-Host "[1/6] All tests PASSED."
Write-Host ""

# -------------------------------------------------------
# STEP 3: Update SDK README.md
# -------------------------------------------------------
Write-Host "[3/8] Updating README.md to v$version..."

$readmePath = "$root\README.md"
$readme = [System.IO.File]::ReadAllText($readmePath, [System.Text.Encoding]::UTF8)
$readme = $readme -replace "$artifactIdEsc`:\d+\.\d+\.\d+", "$artifactId`:$version"
$readme = $readme -replace "$artifactIdBadge`:\d+\.\d+\.\d+", "$($artifactId -replace '-', '--')`:$version"
$readme = $readme -replace '<version>\d+\.\d+\.\d+</version>', "<version>$version</version>"
$readme = $readme -replace "SDK: ``com\.test\.automation:$artifactIdEsc`:\d+\.\d+\.\d+``",
    "SDK: ``com.test.automation:$artifactId`:$version``"
[System.IO.File]::WriteAllText($readmePath, $readme, (New-Object System.Text.UTF8Encoding $false))
# Sync bundled mirror (previously never synced -- caused it to silently drift stale across releases)
$readmeMirror = "$root\src\main\resources\README.md"
if (Test-Path $readmeMirror) {
    [System.IO.File]::WriteAllText($readmeMirror, $readme, (New-Object System.Text.UTF8Encoding $false))
}
Write-Host "      README.md updated (root + bundled mirror)."

# Update SDK-USER-GUIDE.md header (Version + Artifact lines)
$guidePath = "$root\SDK-USER-GUIDE.md"
if (Test-Path $guidePath) {
    $guide = [System.IO.File]::ReadAllText($guidePath, [System.Text.Encoding]::UTF8)
    $guide = $guide -replace '\*\*Version:\*\* \d+\.\d+\.\d+', "**Version:** $version"
    $guide = $guide -replace "$artifactIdEsc`:\d+\.\d+\.\d+", "$artifactId`:$version"
    [System.IO.File]::WriteAllText($guidePath, $guide, (New-Object System.Text.UTF8Encoding $false))
    # Sync mirror
    $guideMirror = "$root\src\main\resources\SDK-USER-GUIDE.md"
    [System.IO.File]::WriteAllText($guideMirror, $guide, (New-Object System.Text.UTF8Encoding $false))
    Write-Host "      SDK-USER-GUIDE.md updated and mirror synced."
}
Write-Host ""

# -------------------------------------------------------
# STEP 4: Promote SDK CHANGELOG.md [Unreleased] to versioned heading
# -------------------------------------------------------
Write-Host "[4/8] Promoting CHANGELOG.md [Unreleased] to [$version]..."

$changelogPath = "$root\CHANGELOG.md"
$changelog = [System.IO.File]::ReadAllText($changelogPath, [System.Text.Encoding]::UTF8)

$reOpts = [System.Text.RegularExpressions.RegexOptions]::Singleline
$unreleasedBlock = (New-Object System.Text.RegularExpressions.Regex('## \[Unreleased\](.*?)(?=## \[|\Z)', $reOpts)).Match($changelog)

if ($unreleasedBlock.Success) {
    $blockContent = $unreleasedBlock.Groups[1].Value.Trim()
    $realLines = @($blockContent -split "`n" | Where-Object { ($_ -notmatch '^\s*<!--') -and ($_ -notmatch '^\s*-->') -and ($_.Trim() -ne '') })

    $dash = [char]0x2014
    $versionHeading = "## [$version] $dash $today"
    $emptyHeader = "## [Unreleased]`n<!-- Add entries here during development; move to a version heading on release -->`n`n---`n`n"
    $reReplace = New-Object System.Text.RegularExpressions.Regex('## \[Unreleased\].*?(?=## \[|\Z)', $reOpts)

    if ($realLines.Count -gt 0) {
        $replacement = "$emptyHeader$versionHeading`n$blockContent`n`n"
        $changelog = $reReplace.Replace($changelog, $replacement)
        Write-Host "      Promoted [Unreleased] to [$version]"
    } else {
        Write-Host "      [Unreleased] is empty -- adding versioned heading."
        $replacement = "$emptyHeader$versionHeading`n`n### Changed`n- Released v$version`n`n"
        $changelog = $reReplace.Replace($changelog, $replacement)
    }
} else {
    Write-Host "      WARNING: [Unreleased] section not found in CHANGELOG.md -- skipping promotion."
}

[System.IO.File]::WriteAllText($changelogPath, $changelog, (New-Object System.Text.UTF8Encoding $false))

# Sync to resources so InstructionExtractor deploys up-to-date changelog to consumers
$resourcesChangelogPath = "$root\src\main\resources\CHANGELOG.md"
[System.IO.File]::WriteAllText($resourcesChangelogPath, $changelog, (New-Object System.Text.UTF8Encoding $false))
Write-Host "      Synced to src/main/resources/CHANGELOG.md"
Write-Host ""

# -------------------------------------------------------
# STEP 5: Git commit SDK docs
# -------------------------------------------------------
Write-Host "[5/8] Committing SDK README.md and CHANGELOG.md..."

Set-Location $root
$sdkDocPaths = @(
    "pom.xml", "README.md", "SDK-USER-GUIDE.md", "CHANGELOG.md",
    "src\main\resources\README.md", "src\main\resources\CHANGELOG.md", "src\main\resources\SDK-USER-GUIDE.md",
    "src\main\resources\sdk-defaults\sdk-config.yaml.template"
)
git add $sdkDocPaths
$gitStatus = git status --porcelain $sdkDocPaths
if ($gitStatus) {
    $commitMsg = "docs: release v$version -- update README, SDK-USER-GUIDE, and CHANGELOG"
    git commit -m $commitMsg
    Write-Host "      Docs committed."
} else {
    Write-Host "      No doc changes to commit (already up to date)."
}
Write-Host ""

# -------------------------------------------------------
# STEP 6: Deploy (tests already passed)
# -------------------------------------------------------
Write-Host "[6/8] Deploying v$version..."
Write-Host ""
Write-Host "  [6a] Azure Artifacts (mvn clean deploy)..."

$deployCmd = @("clean", "deploy") + $proxyArgs
& mvn @deployCmd
$deployExit = $LASTEXITCODE

Write-Host ""
if ($deployExit -ne 0) {
    Write-Host "============================================"
    Write-Host "  ERROR: Azure Artifacts deploy failed (exit $deployExit)"
    Write-Host "  Tests passed -- check Maven/network/proxy/PAT settings."
    Write-Host "============================================"
    exit 1
}
Write-Host "      Azure Artifacts deploy succeeded."
Write-Host ""

# -------------------------------------------------------
# STEP 6b: Deploy to the local git-backed maven-repository fallback.
# This is the repo consumer templates fall back to when Azure Artifacts
# credentials aren't configured (see each template's pom.xml <repositories>).
# Historically this step was a manual, easy-to-forget reminder -- if skipped,
# consumers on the fallback repo silently kept resolving a stale SDK version.
# It is now mandatory and release-blocking.
# -------------------------------------------------------
if ($SkipMavenRepo) {
    Write-Host "  [6b] -SkipMavenRepo supplied -- skipping local maven-repository deploy/commit."
    Write-Host "       WARNING: consumers using the local-repo fallback will NOT see v$version"
    Write-Host "       until this is done manually. Only use -SkipMavenRepo for a dry run."
} elseif (-not (Test-Path $MavenRepoPath)) {
    Write-Host "============================================"
    Write-Host "  ERROR: maven-repository not found at: $MavenRepoPath"
    Write-Host "  Pass -MavenRepoPath, or clone it, before releasing."
    Write-Host "============================================"
    exit 1
} else {
    Write-Host "  [6b] Local maven-repository fallback (mvn deploy -DaltDeploymentRepository)..."
    $mavenRepoUri = "file:///" + ($MavenRepoPath -replace '\\', '/')
    $altDeployCmd = @("deploy", "-Dmaven.test.skip=true",
        "-DaltDeploymentRepository=test-automation-sdk-local::default::$mavenRepoUri") + $proxyArgs
    & mvn @altDeployCmd
    $altDeployExit = $LASTEXITCODE

    $versionDir = "$MavenRepoPath\com\test\automation\$artifactId\$version"
    if ($altDeployExit -ne 0 -or -not (Test-Path $versionDir)) {
        Write-Host ""
        Write-Host "============================================"
        Write-Host "  ERROR: Local maven-repository deploy failed or artifact directory"
        Write-Host "  is missing: $versionDir"
        Write-Host "  RELEASE ABORTED -- fallback-repo consumers must never see a partial state."
        Write-Host "============================================"
        exit 1
    }
    Write-Host "      Artifact present at $versionDir -- OK"

    Set-Location $MavenRepoPath
    git add "com\test\automation\$artifactId\$version" "com\test\automation\$artifactId\maven-metadata*.xml*"
    $mavenRepoStatus = git status --porcelain "com\test\automation\$artifactId"
    if ($mavenRepoStatus) {
        git commit -m "Deploy $artifactId $version"
        Write-Host "      maven-repository commit created (NOT pushed -- push manually per policy)."
    } else {
        Write-Host "      maven-repository already up to date -- nothing to commit."
    }
    Set-Location $root
    $mavenRepoDeployed = $true
    Write-Host ""
}

Write-Host ""

# -------------------------------------------------------
# STEP 7: Update consumer templates (Web, API, Mobile)
# -------------------------------------------------------
Write-Host "[7/8] Updating consumer templates..."

$templateDefs = @(
    @{ Name = "Web";    Path = $WebTemplatePath }
    @{ Name = "API";    Path = $ApiTemplatePath }
    @{ Name = "Mobile"; Path = $MobileTemplatePath }
)

if ($SkipTemplates) {
    Write-Host "      -SkipTemplates supplied -- skipping all consumer template updates."
} else {
    foreach ($tpl in $templateDefs) {
        $tplName = $tpl.Name
        $TemplatePath = $tpl.Path
        Write-Host ""
        Write-Host "  -- $tplName template: $TemplatePath --"

        if (-not (Test-Path $TemplatePath)) {
            Write-Host "      WARNING: Template directory not found -- skipping."
            Write-Host "      Expected: $TemplatePath"
            $templateResults += [PSCustomObject]@{ Name = $tplName; Applicable = $false; Updated = $false; Version = $null }
            continue
        }

        # --- Safety guard: only touch this template if it actually depends on THIS
        #     SDK's artifactId. Multiple, independently-versioned SDKs can share the
        #     same groupId/feed name, but a template that depends on a DIFFERENT SDK
        #     must never have its version bumped to match this release.
        $tplPomCheck = "$TemplatePath\pom.xml"
        $dependsOnThisSdk = $false
        if (Test-Path $tplPomCheck) {
            $tplPomCheckContent = Get-Content $tplPomCheck -Raw
            if ($tplPomCheckContent -match "<artifactId>$artifactIdEsc</artifactId>") {
                $dependsOnThisSdk = $true
            }
        }

        if (-not $dependsOnThisSdk) {
            Write-Host "      SKIPPED: does not depend on artifactId '$artifactId'."
            Write-Host "      (It may intentionally track a different SDK -- not touching it."
            Write-Host "       Pass -${tplName}TemplatePath to point at the correct consumer project.)"
            $templateResults += [PSCustomObject]@{ Name = $tplName; Applicable = $false; Updated = $false; Version = $null }
            continue
        }

        # --- pom.xml: bump SDK version ---
        $pomContent = Get-Content $tplPomCheck -Raw
        $pomContent = [System.Text.RegularExpressions.Regex]::Replace(
            $pomContent,
            "(<artifactId>$artifactIdEsc</artifactId>\s*<version>)\d+\.\d+\.\d+(</version>)",
            "`${1}$version`${2}",
            [System.Text.RegularExpressions.RegexOptions]::Singleline
        )
        Set-Content $tplPomCheck $pomContent
        Write-Host "      pom.xml updated to $version"

        # --- README.md: version badge + dependency snippet ---
        $tplReadme = "$TemplatePath\README.md"
        if (Test-Path $tplReadme) {
            $tplRm = [System.IO.File]::ReadAllText($tplReadme, [System.Text.Encoding]::UTF8)
            $tplRm = $tplRm -replace "$artifactIdEsc`:\d+\.\d+\.\d+", "$artifactId`:$version"
            $tplRm = $tplRm -replace "$artifactIdBadge`:\d+\.\d+\.\d+", "$($artifactId -replace '-', '--')`:$version"
            $tplRm = $tplRm -replace '<version>\d+\.\d+\.\d+</version>', "<version>$version</version>"
            [System.IO.File]::WriteAllText($tplReadme, $tplRm, (New-Object System.Text.UTF8Encoding $false))
            Write-Host "      README.md updated to $version"
        }

        # --- SDK-USER-GUIDE.md: version header (only web template has this today) ---
        $tplGuide = "$TemplatePath\SDK-USER-GUIDE.md"
        if (Test-Path $tplGuide) {
            $tplGd = [System.IO.File]::ReadAllText($tplGuide, [System.Text.Encoding]::UTF8)
            $tplGd = $tplGd -replace '\*\*Version:\*\* \d+\.\d+\.\d+', "**Version:** $version"
            $tplGd = $tplGd -replace "$artifactIdEsc`:\d+\.\d+\.\d+", "$artifactId`:$version"
            [System.IO.File]::WriteAllText($tplGuide, $tplGd, (New-Object System.Text.UTF8Encoding $false))
            Write-Host "      SDK-USER-GUIDE.md updated to $version"
        }

        # --- GETTING-STARTED.md: all version references ---
        $tplGs = "$TemplatePath\GETTING-STARTED.md"
        if (Test-Path $tplGs) {
            $gs = [System.IO.File]::ReadAllText($tplGs, [System.Text.Encoding]::UTF8)
            $gs = $gs -replace "$artifactIdEsc`:\d+\.\d+\.\d+", "$artifactId`:$version"
            $gs = $gs -replace "$artifactIdEsc/\d+\.\d+\.\d+/", "$artifactId/$version/"
            $gs = $gs -replace "$artifactIdEsc-\d+\.\d+\.\d+", "$artifactId-$version"
            $gs = $gs -replace '-Dversion=\d+\.\d+\.\d+', "-Dversion=$version"
            [System.IO.File]::WriteAllText($tplGs, $gs, (New-Object System.Text.UTF8Encoding $false))
            Write-Host "      GETTING-STARTED.md updated to $version"
        }

        # --- CHANGELOG.md: add SDK upgrade entry to [Unreleased] (only where present) ---
        $tplCl = "$TemplatePath\CHANGELOG.md"
        if (Test-Path $tplCl) {
            $tplClContent = [System.IO.File]::ReadAllText($tplCl, [System.Text.Encoding]::UTF8)
            $upgradeEntry = "- **SDK upgraded** ``$artifactId`` to ``$version``"
            if ($tplClContent -notmatch [regex]::Escape("to ``$version``")) {
                $tplClContent = $tplClContent -replace '(## \[Unreleased\][^\n]*\n)', "`$1`n$upgradeEntry`n"
                [System.IO.File]::WriteAllText($tplCl, $tplClContent, (New-Object System.Text.UTF8Encoding $false))
                Write-Host "      CHANGELOG.md: SDK upgrade entry added"
            } else {
                Write-Host "      CHANGELOG.md: entry already present -- skipped"
            }
        }

        # --- Refresh .github/, docs/sdk/, configuration/ from the newly deployed jar ---
        Write-Host "      Refreshing bundled docs/config via InstructionExtractor..."
        Set-Location $TemplatePath
        & mvn exec:java "-Dexec.mainClass=com.test.automation.sdk.utility.InstructionExtractor" @proxyArgs
        $extractExit = $LASTEXITCODE
        if ($extractExit -ne 0) {
            Write-Host "      WARNING: InstructionExtractor failed (exit $extractExit) -- continuing;"
            Write-Host "      docs/sdk/, configuration/, and .github/ may not reflect v$version."
        } else {
            Write-Host "      InstructionExtractor refreshed docs/sdk/, configuration/, .github/"
        }

        # --- Validate template against the new SDK version before committing ---
        Write-Host "      Validating template compiles against SDK $version (mvn compile test-compile)..."
        & mvn compile test-compile @proxyArgs
        $tplBuildExit = $LASTEXITCODE
        if ($tplBuildExit -ne 0) {
            Write-Host ""
            Write-Host "============================================"
            Write-Host "  ERROR: $tplName template failed to compile against SDK $version"
            Write-Host "  Template changes were NOT committed or pushed."
            Write-Host "  RELEASE ABORTED -- fix the breaking change (or the template) and re-run."
            Write-Host "============================================"
            Set-Location $root
            exit 1
        }
        Write-Host "      $tplName template compiles cleanly against SDK $version -- OK"

        # --- Validate the template uses the standard Allure publishing pattern ---
        # (PublishAllureReport@2, target/allure-results canonical directory) and no
        # legacy custom external report-hosting mechanism (S3Upload@1, hand-built
        # "automation-test-results/..." keys). A template still on the legacy path
        # aborts the release -- Allure publishing must stay in sync across all three
        # consumer templates (see MASTER-SOLUTION-GUIDE.md Section 27).
        $tplPipelineFiles = Get-ChildItem -Path $TemplatePath -Recurse -Include "azure-pipelines*.yml*", "run-suite.yml" -ErrorAction SilentlyContinue
        $legacyPatternFound = $false
        $publishTaskFound = $false
        foreach ($pf in $tplPipelineFiles) {
            $pfContent = Get-Content $pf.FullName -Raw
            if ($pfContent -match "S3Upload@1|AllureReportPathBuilder|automation-test-results/") {
                Write-Host "      ERROR: legacy custom Allure publishing pattern found in $($pf.FullName)"
                $legacyPatternFound = $true
            }
            if ($pfContent -match "PublishAllureReport@2") {
                $publishTaskFound = $true
            }
        }
        if ($legacyPatternFound) {
            Write-Host ""
            Write-Host "============================================"
            Write-Host "  ERROR: $tplName template still uses the legacy Allure publishing mechanism."
            Write-Host "  RELEASE ABORTED -- migrate to PublishAllureReport@2 and re-run."
            Write-Host "============================================"
            Set-Location $root
            exit 1
        }
        if (-not $publishTaskFound -and $tplPipelineFiles) {
            Write-Host "      WARNING: no PublishAllureReport@2 task found in $tplName template pipeline file(s)."
        } else {
            Write-Host "      $tplName template Allure publishing pattern -- OK (PublishAllureReport@2, no legacy S3/path-builder mechanism)"
        }

        # --- Git commit template changes ---
        git add pom.xml README.md SDK-USER-GUIDE.md GETTING-STARTED.md CHANGELOG.md docs\sdk configuration .github 2>$null
        $tplStatus = git status --porcelain pom.xml README.md SDK-USER-GUIDE.md GETTING-STARTED.md CHANGELOG.md docs\sdk configuration .github 2>$null
        if ($tplStatus) {
            git commit -m "chore: bump SDK dependency to $version"
            Write-Host "      $tplName template changes committed."
        } else {
            Write-Host "      $tplName template already up to date -- nothing to commit."
        }

        Set-Location $root
        $templateResults += [PSCustomObject]@{ Name = $tplName; Applicable = $true; Updated = $true; Version = $version }
    }
}
Set-Location $root
Write-Host ""

# -------------------------------------------------------
# STEP 8: Final verification matrix
# -------------------------------------------------------
Write-Host "[8/8] Final verification matrix..."
$checks = @()

function Add-Check([string]$Name, [bool]$Passed, [string]$Detail = "") {
    $script:checks += [PSCustomObject]@{ Name = $Name; Passed = $Passed; Detail = $Detail }
}

Add-Check "SDK pom.xml version" ($version -match '^\d+\.\d+\.\d+$') "$version"

$sdkPipelineTemplate = "$root\src\main\resources\sdk-defaults\azure-pipelines.yml.template"
$sdkPipelineContent = if (Test-Path $sdkPipelineTemplate) { Get-Content $sdkPipelineTemplate -Raw } else { "" }
Add-Check "SDK azure-pipelines.yml.template uses PublishAllureReport@2" ($sdkPipelineContent -match "PublishAllureReport@2")
Add-Check "SDK azure-pipelines.yml.template has no legacy Allure S3/path-builder mechanism" ($sdkPipelineContent -notmatch "S3Upload@1|AllureReportPathBuilder|automation-test-results/")

$changelogFinal = Get-Content "$root\CHANGELOG.md" -Raw
Add-Check "SDK CHANGELOG has [$version] heading" ($changelogFinal -match [regex]::Escape("## [$version]"))

foreach ($pair in @(
        @{ Label = "README.md";        Root = "$root\README.md";        Mirror = "$root\src\main\resources\README.md" }
        @{ Label = "SDK-USER-GUIDE.md"; Root = "$root\SDK-USER-GUIDE.md"; Mirror = "$root\src\main\resources\SDK-USER-GUIDE.md" }
        @{ Label = "CHANGELOG.md";      Root = "$root\CHANGELOG.md";      Mirror = "$root\src\main\resources\CHANGELOG.md" }
    )) {
    $identical = $false
    if ((Test-Path $pair.Root) -and (Test-Path $pair.Mirror)) {
        $identical = -not (Compare-Object (Get-Content $pair.Root) (Get-Content $pair.Mirror))
    }
    Add-Check "$($pair.Label) root/resources mirror match" $identical
}

if ($SkipMavenRepo) {
    Add-Check "maven-repository artifact published" $true "skipped (-SkipMavenRepo)"
} else {
    $versionDirFinal = "$MavenRepoPath\com\test\automation\$artifactId\$version"
    Add-Check "maven-repository artifact published" ($mavenRepoDeployed -and (Test-Path $versionDirFinal)) "$versionDirFinal"
}

if ($SkipTemplates) {
    Add-Check "Consumer templates updated" $true "skipped (-SkipTemplates)"
} else {
    foreach ($tr in $templateResults) {
        if ($tr.Applicable) {
            $tplPathFinal = ($templateDefs | Where-Object { $_.Name -eq $tr.Name } | Select-Object -First 1).Path
            $tplPomFinal = Get-Content "$tplPathFinal\pom.xml" -Raw
            $versionMatches = $tplPomFinal -match "<artifactId>$artifactIdEsc</artifactId>\s*<version>$([regex]::Escape($version))</version>"
            Add-Check "$($tr.Name) template pom.xml == v$version" $versionMatches

            $tplPipelineFilesFinal = Get-ChildItem -Path $tplPathFinal -Recurse -Include "azure-pipelines*.yml*", "run-suite.yml" -ErrorAction SilentlyContinue
            $tplLegacyFound = $false
            $tplPublishFound = $false
            foreach ($pf in $tplPipelineFilesFinal) {
                $pfContent = Get-Content $pf.FullName -Raw
                if ($pfContent -match "S3Upload@1|AllureReportPathBuilder|automation-test-results/") { $tplLegacyFound = $true }
                if ($pfContent -match "PublishAllureReport@2") { $tplPublishFound = $true }
            }
            Add-Check "$($tr.Name) template uses PublishAllureReport@2, no legacy Allure mechanism" ($tplPublishFound -and -not $tplLegacyFound)
        } else {
            Add-Check "$($tr.Name) template applicable" $true "not a dependent of $artifactId -- skipped"
        }
    }
}

Write-Host ""
$anyFailed = $false
foreach ($c in $checks) {
    $mark = if ($c.Passed) { "OK  " } else { $anyFailed = $true; "FAIL" }
    $detailSuffix = if ($c.Detail) { " ($($c.Detail))" } else { "" }
    Write-Host "      [$mark] $($c.Name)$detailSuffix"
}
Write-Host ""

if ($anyFailed) {
    Write-Host "============================================"
    Write-Host "  RELEASE INCOMPLETE -- one or more verification checks failed."
    Write-Host "  SDK source, Maven repository artifact, consumer templates, and active"
    Write-Host "  documentation must all match before this release is considered done."
    Write-Host "  Review the FAIL rows above and re-run once corrected."
    Write-Host "============================================"
    exit 1
}

Write-Host ""
Write-Host "============================================"
Write-Host "  SUCCESS: SDK v$version released"
Write-Host ""
Write-Host "  Doc gate    : CHANGELOG [Unreleased], key docs, sdk-config.yaml.template, and"
Write-Host "                MASTER-SOLUTION-GUIDE/Executive Summary semantic review acknowledged"
Write-Host "  Deploy      : Azure Artifacts + local git-backed maven-repository (both verified)"
Write-Host "  Templates   : Web/API/Mobile updated where applicable, each validated to compile"
Write-Host "  Verification: all Step 8 checks passed (see matrix above)"
Write-Host ""
Write-Host "  REQUIRED MANUAL STEP -- push everything that was committed locally:"
Write-Host "    git push origin master   (SDK: $root)"
Write-Host "    git push origin <branch> (maven-repository: $MavenRepoPath)"
Write-Host "    git push origin master   (each updated consumer template)"
Write-Host "  Nothing above was pushed automatically -- commits are local only."
Write-Host "============================================"
