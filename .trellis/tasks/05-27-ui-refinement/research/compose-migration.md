# Compose migration research

## Sources consulted

* Context7 library result: `/websites/developer_android_develop_ui_compose`
* Context7 library result: `/websites/developer_android_develop_ui_compose_migrate`
* Android Developers: Compose in Views / migration interoperability APIs
* Android Developers: Compose compiler setup

## Relevant documentation findings

* Android's recommended Activity entry point for Compose is `ComponentActivity.setContent { ... }`, with the composable tree placed inside a Material theme.
* The app should use Compose Material 3 for new Compose screens when the design system can move to Material 3.
* Material components such as `Button` and `Text` must be emitted under a `MaterialTheme`.
* Gradual migration is supported with View/Compose interoperability.
* Existing Android Views can be embedded into Compose using interop patterns. This matters for the AMap WebView picker if it is migrated in this task.
* Compose dependencies should use the Compose BOM to keep Compose library versions aligned.
* For Kotlin 2.x projects, the Compose compiler plugin is configured through `org.jetbrains.kotlin.plugin.compose` with a version matching Kotlin.
* Compose UI should use `stringResource(R.string...)` for localized strings rather than hard-coded UI text. Android recreates Activities on configuration changes and `stringResource` reads the current configuration.

## Repo mapping

* Current Kotlin version: `2.1.0`.
* Current Android Gradle Plugin version: `8.7.3`.
* Current Gradle wrapper: `8.12`.
* Current app module has no Compose setup.
* `MainActivity` can become the first Compose screen by replacing `setContentView(buildUi())` with `setContent { TrackWriteTheme { ... } }`.
* `ManualLocationActivity` can either remain View-based for this task or be migrated with Compose layout plus WebView interop.
* Chinese support should be implemented with Android string resources, including default strings and a Chinese resource folder such as `values-zh-rCN`.

## Recommended migration shape

Use Compose as the new product UI foundation rather than a thin wrapper around existing View code:

* Add Compose Gradle support and Material 3.
* Create a small Compose design system package: theme, tokens, reusable buttons/status chips/cards/list rows.
* Rebuild `MainActivity` as a workflow-first Compose screen.
* Keep domain/data/media/recording behavior stable and call existing repositories/services from Activity-level handlers.
* Decide separately whether `ManualLocationActivity` is included in the first migration pass because WebView interop adds risk.

## Implementation risks

* A full Compose migration can accidentally mix UI state and side effects in one large Activity if not structured carefully.
* The existing app has no ViewModel layer; preserving behavior while improving UI may require a small UI state model inside `MainActivity` or new UI package files.
* If `ManualLocationActivity` is migrated, the AMap WebView JS bridge and lifecycle must be preserved exactly.
* Compose dependencies may require downloading artifacts during the first Gradle run.
