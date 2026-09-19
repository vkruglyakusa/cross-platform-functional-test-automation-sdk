# SDK 1.5.2 integration verification

This record covers the product-neutral merge of the supplied SDK 1.5.2 source
into the maintained GitHub SDK as `1.5.3-SNAPSHOT`. The development version is
intentional: maintained remote-provider functionality and additional compatibility
fixes were retained rather than republishing the supplied 1.5.2 artifact unchanged.

## Merge boundaries

- Imported the delivered API, evidence, analytics, accessibility, healing,
  visual-regression, impact-analysis, crawler, and reporting capabilities.
- Preserved the maintained provider-aware execution context, factory registry,
  remote Selenium/Appium adapters, GitHub Packages publishing target, and pinned
  BrowserStack dependency.
- Carried the 1.5.2 Allure lifecycle-race correction and configurable
  UiAutomator2 server launch timeout.
- Added optional Appium app-wait and no-reset capabilities to support older
  neutral sample apps without changing default behavior.
- Kept `.migration/`, generated reports, credentials, and sample APK binaries out
  of version control.

## Verification on 2026-09-19

| Gate | Result |
|---|---|
| SDK `mvn -o -q test` | 760 tests; 0 failures, 0 errors, 0 skipped |
| Web template `mvn -o -q test` | Wikipedia Chrome smoke passed |
| API template `mvn -o -q test` | JSONPlaceholder GET and POST smokes passed |
| Mobile template `mvn -o -q -Plocal '-DdeviceName=Pixel_10_Pro' '-Dandroid.noReset=true' test` | Wikipedia sample APK smoke passed on the connected Android emulator |
| Web/Mobile/API setup scripts `-Offline` | All three test-source compilations passed against the locally installed SDK |

The Android smoke uses `-Dandroid.noReset=true` because the older Wikipedia
sample APK can trigger an OS compatibility dialog after installation. The page
object waits for and dismisses that dialog. This is a sample-app concern, not
a required setting for consumer applications.

The local LLM was invoked through the dev gateway for a bounded timeout-test
slice. It made no edit; Claude review rejected that result. The regression
tests were then added and validated by the full Maven suite. No gateway output
is treated as accepted merely because its model call completed.

## Remaining release gates

This is a validated development snapshot, not a tagged release. Publish the SDK
package, verify clean-clone resolution in CI, and complete the planned Studio
capability/template registry and generated-project compatibility checks before
tagging a release.
