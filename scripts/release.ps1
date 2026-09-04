# SDK Release Script (Windows PowerShell)
# Usage: scripts\release.ps1 [-ProxyHost bcpxy.nycnet] [-ProxyPort 8080] [-AssumeYes]
#   -AssumeYes: skip the interactive "docs may need updating" confirmation prompt
#               (required for non-interactive/automated runs; use only when you have
#               already verified docs don't need updating for this release)
#
# Full release pipeline -- runs automatically in order:
#   1. Doc check gate              -- confirm CHANGELOG [Unreleased] and TESTBASE-API/SDK-USER-GUIDE are updated
#   2. Run ALL tests               -- abort if any fail
#   3. Update SDK README.md        -- version badge, dependency snippet, footer
#   4. Promote SDK CHANGELOG.md    -- move [Unreleased] -> [version] -- date
#   5. Git commit SDK docs         -- single commit: "docs: release vX.Y.Z"
#   6. Deploy                      -- Maven deploy to Azure Artifacts + local repo
#   7. Update consumer template    -- pom.xml, README.md, GETTING-STARTED.md, CHANGELOG.md
#                                     then validate with `mvn compile test-compile` before
#                                     committing -- template changes are NEVER committed/pushed
#                                     if this validation fails
#
# NEVER use `mvn deploy -DskipTests` directly. Always use this script.

param(
    [string]$ProxyHost = "",
    [string]$ProxyPort = "8080",
    [string]$TemplatePath = "",
    [switch]$AssumeYes
)

$ErrorActionPreference = "Continue"
$root = Split-Path -Parent $PSScriptRoot

# Resolve template path -- default is sibling directory
if ($TemplatePath -eq "") {
    $TemplatePath = (Split-Path -Parent $root) + "\functional-automation-consumer-template"
}

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
$today   = (Get-Date).ToString("yyyy-MM-dd")

Write-Host ""
Write-Host "============================================"
Write-Host "  SDK Release Pipeline - v$version"
Write-Host "============================================"
Write-Host ""

# -------------------------------------------------------
# STEP 1: Doc check gate -- confirm docs are updated before anything else
# -------------------------------------------------------
Write-Host "[1/7] Doc check gate..."
Write-Host "      Checking CHANGELOG.md has an [Unreleased] section with content..."
$changelogContent = Get-Content "$root\CHANGELOG.md" -Raw
$unreleasedMatch = [System.Text.RegularExpressions.Regex]::Match(
    $changelogContent,
    '## \[Unreleased\]\s*\n(.*?)(\n## \[|\z)',
    [System.Text.RegularExpressions.RegexOptions]::Singleline
)
$unreleasedBody = if ($unreleasedMatch.Success) { $unreleasedMatch.Groups[1].Value.Trim() } else { "" }

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
    Write-Host "    5. Sync changes to src/main/resources/ mirrors"
    Write-Host "============================================"
    exit 1
}
Write-Host "      CHANGELOG.md [Unreleased] has content -- OK"

# Warn (don't block) if key doc files haven't been touched since last commit
$docFiles = @("TESTBASE-API.md", "SDK-USER-GUIDE.md", ".github\instructions\test-creation.instructions.md")
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

# STEP 2: Run all tests (mandatory -- abort on any failure)
# -------------------------------------------------------
Write-Host "[2/7] Running all tests (mvn clean test)..."
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
Write-Host "[3/7] Updating README.md to v$version..."

$readmePath = "$root\README.md"
$readme = [System.IO.File]::ReadAllText($readmePath, [System.Text.Encoding]::UTF8)
$readme = $readme -replace 'functional-test-automation-sdk:\d+\.\d+\.\d+', "functional-test-automation-sdk:$version"
$readme = $readme -replace 'functional--test--automation--sdk:\d+\.\d+\.\d+', "functional--test--automation--sdk:$version"
$readme = $readme -replace '<version>\d+\.\d+\.\d+</version>', "<version>$version</version>"
$readme = $readme -replace 'SDK: `com\.test\.automation:functional-test-automation-sdk:\d+\.\d+\.\d+`',
    "SDK: ``com.test.automation:functional-test-automation-sdk:$version``"
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
    $guide = $guide -replace 'functional-test-automation-sdk:\d+\.\d+\.\d+', "functional-test-automation-sdk:$version"
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
Write-Host "[4/7] Promoting CHANGELOG.md [Unreleased] to [$version]..."

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
Write-Host "[5/7] Committing SDK README.md and CHANGELOG.md..."

Set-Location $root
git add README.md SDK-USER-GUIDE.md CHANGELOG.md src\main\resources\CHANGELOG.md src\main\resources\SDK-USER-GUIDE.md
$gitStatus = git status --porcelain README.md SDK-USER-GUIDE.md CHANGELOG.md src\main\resources\CHANGELOG.md src\main\resources\SDK-USER-GUIDE.md
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
Write-Host "[6/7] Deploying v$version (mvn clean deploy)..."
Write-Host ""

$deployCmd = @("clean", "deploy") + $proxyArgs
& mvn @deployCmd
$deployExit = $LASTEXITCODE

Write-Host ""
if ($deployExit -ne 0) {
    Write-Host "============================================"
    Write-Host "  ERROR: Deploy failed (exit $deployExit)"
    Write-Host "  Tests passed -- check Maven/network logs."
    Write-Host "============================================"
    exit 1
}

Write-Host "      Deploy succeeded."
Write-Host ""

# -------------------------------------------------------
# STEP 7: Update consumer template project
# -------------------------------------------------------
Write-Host "[7/7] Updating consumer template at: $TemplatePath"

if (-not (Test-Path $TemplatePath)) {
    Write-Host "      WARNING: Template directory not found -- skipping template update."
    Write-Host "      Expected: $TemplatePath"
} else {

    # --- pom.xml: bump SDK version ---
    $tplPom = "$TemplatePath\pom.xml"
    if (Test-Path $tplPom) {
        $pomContent = Get-Content $tplPom -Raw
        # Replace ONLY the SDK dependency version -- match the artifactId line followed by version
        $pomContent = [System.Text.RegularExpressions.Regex]::Replace(
            $pomContent,
            '(<artifactId>functional-test-automation-sdk</artifactId>\s*<version>)\d+\.\d+\.\d+(</version>)',
            "`${1}$version`${2}",
            [System.Text.RegularExpressions.RegexOptions]::Singleline
        )
        Set-Content $tplPom $pomContent
        Write-Host "      pom.xml updated to $version"
    }

    # --- README.md: version badge + dependency snippet ---
    $tplReadme = "$TemplatePath\README.md"
    if (Test-Path $tplReadme) {
        $tplRm = [System.IO.File]::ReadAllText($tplReadme, [System.Text.Encoding]::UTF8)
        $tplRm = $tplRm -replace 'functional-test-automation-sdk:\d+\.\d+\.\d+', "functional-test-automation-sdk:$version"
        $tplRm = $tplRm -replace 'functional--test--automation--sdk:\d+\.\d+\.\d+', "functional--test--automation--sdk:$version"
        $tplRm = $tplRm -replace '<version>\d+\.\d+\.\d+</version>', "<version>$version</version>"
        [System.IO.File]::WriteAllText($tplReadme, $tplRm, (New-Object System.Text.UTF8Encoding $false))
        Write-Host "      README.md updated to $version"
    }

    # --- SDK-USER-GUIDE.md: version header ---
    $tplGuide = "$TemplatePath\SDK-USER-GUIDE.md"
    if (Test-Path $tplGuide) {
        $tplGd = [System.IO.File]::ReadAllText($tplGuide, [System.Text.Encoding]::UTF8)
        $tplGd = $tplGd -replace '\*\*Version:\*\* \d+\.\d+\.\d+', "**Version:** $version"
        $tplGd = $tplGd -replace 'functional-test-automation-sdk:\d+\.\d+\.\d+', "functional-test-automation-sdk:$version"
        [System.IO.File]::WriteAllText($tplGuide, $tplGd, (New-Object System.Text.UTF8Encoding $false))
        Write-Host "      SDK-USER-GUIDE.md updated to $version"
    }

    # --- GETTING-STARTED.md: all version references ---
    $tplGs = "$TemplatePath\GETTING-STARTED.md"
    if (Test-Path $tplGs) {
        $gs = [System.IO.File]::ReadAllText($tplGs, [System.Text.Encoding]::UTF8)
        $gs = $gs -replace 'functional-test-automation-sdk:\d+\.\d+\.\d+', "functional-test-automation-sdk:$version"
        $gs = $gs -replace 'functional-test-automation-sdk/\d+\.\d+\.\d+/', "functional-test-automation-sdk/$version/"
        $gs = $gs -replace 'functional-test-automation-sdk-\d+\.\d+\.\d+', "functional-test-automation-sdk-$version"
        $gs = $gs -replace '-Dversion=\d+\.\d+\.\d+', "-Dversion=$version"
        [System.IO.File]::WriteAllText($tplGs, $gs, (New-Object System.Text.UTF8Encoding $false))
        Write-Host "      GETTING-STARTED.md updated to $version"
    }

    # --- CHANGELOG.md: add SDK upgrade entry to [Unreleased] ---
    $tplCl = "$TemplatePath\CHANGELOG.md"
    if (Test-Path $tplCl) {
        $tplClContent = [System.IO.File]::ReadAllText($tplCl, [System.Text.Encoding]::UTF8)
        $upgradeEntry = "- **SDK upgraded** ``functional-test-automation-sdk`` to ``$version``"
        # Only add entry if not already there
        if ($tplClContent -notmatch [regex]::Escape("to ``$version``")) {
            $tplClContent = $tplClContent -replace '(## \[Unreleased\][^\n]*\n)', "`$1`n$upgradeEntry`n"
            [System.IO.File]::WriteAllText($tplCl, $tplClContent, (New-Object System.Text.UTF8Encoding $false))
            Write-Host "      CHANGELOG.md: SDK upgrade entry added"
        } else {
            Write-Host "      CHANGELOG.md: entry already present -- skipped"
        }
    }

    # --- Validate template against the new SDK version before committing ---
    Write-Host "      Validating template compiles against SDK $version (mvn compile test-compile)..."
    Set-Location $TemplatePath
    & mvn compile test-compile @proxyArgs
    $tplBuildExit = $LASTEXITCODE
    if ($tplBuildExit -ne 0) {
        Write-Host ""
        Write-Host "============================================"
        Write-Host "  ERROR: Consumer template failed to compile against SDK $version"
        Write-Host "  Template changes were NOT committed or pushed."
        Write-Host "  Fix the breaking change (or the template) before releasing."
        Write-Host "============================================"
        exit 1
    }
    Write-Host "      Template compiles cleanly against SDK $version -- OK"
    Write-Host ""

    # --- Git commit template changes ---
    Set-Location $TemplatePath
    git add pom.xml README.md SDK-USER-GUIDE.md GETTING-STARTED.md CHANGELOG.md
    $tplStatus = git status --porcelain pom.xml README.md SDK-USER-GUIDE.md GETTING-STARTED.md CHANGELOG.md
    if ($tplStatus) {
        $tplCommitMsg = "chore: bump SDK dependency to $version"
        git commit -m $tplCommitMsg
        Write-Host "      Template changes committed."
    } else {
        Write-Host "      Template already up to date -- nothing to commit."
    }
}

Write-Host ""
Write-Host "============================================"
Write-Host "  SUCCESS: SDK v$version released"
Write-Host ""
Write-Host "  Doc gate    : CHANGELOG [Unreleased] verified, key docs checked"
Write-Host "  Deploy      : Azure Artifacts + local .m2"
Write-Host "  Template    : pom.xml, README, SDK-USER-GUIDE, GETTING-STARTED, CHANGELOG updated"
Write-Host ""
Write-Host "  Push both repos to remote:"
Write-Host "    git push origin master  (SDK)"
Write-Host "    git push origin master  (template)"
Write-Host "  Then update maven-repository with new artifacts."
Write-Host "============================================"
