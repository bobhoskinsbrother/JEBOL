# Notifications, location and the camera: what each platform demands

Research for goals 2e to 2h and decision 78, done on 2026-11-18 from web
searches. The sources are developer forums, vendor pages and manuals as well
as first-party documentation, and each finding says which kind it rests on.
**Each stage's plan re-checks the findings it depends on against the
platform's own current documentation, or a probe, before building on them.**
A finding a plan proves or disproves is corrected here, with the date.

## Rebol

Searched in `rebol3-source/` (the C, the mezz, the boot files, the modules)
and Rebol's suite on 2026-11-18 for `camera`, `webcam`, `geoloc` and
`notif`. Nothing: the only `camera` hits are JEBOL's own example of an
extension (`docs/writing-an-extension.md`, `ExtensionsFromTheSourceTest`,
`CommandsFromTheSourceTest`), and `notif` finds only TLS's `close_notify` and
a comment in `webdriver.reb`. So the REBOL for all three is JEBOL's own.

What R3 does fix:
- **The event types.** `rebol3-source/src/include/reb-evtypes.h` lists them:
  `ignore`, `interrupt`, `device`, `callback`, `custom`, `error`, `init`,
  `open`, `close`, `connect`, `accept`, `read`, `write`, `wrote`, `lookup`,
  `ready`, `done`, `time`, `show`, `hide`, `offset`, `resize`, `active`,
  `inactive`, `minimize`, `maximize`, `restore`, `move`, `down`, `up`, and the
  keys and scrolls after them. There is no `click`.
- **The access errors.** `rebol3-source/src/boot/errors.reb` has
  `cannot-open: [{cannot open:} :arg1 {reason:} :arg2]`, `not-open`,
  `timeout`, `security` and the rest. JEBOL adds `no-service` for a host
  service not granted or not present (`spec/natives.allium`, rule
  `AnIdOnlyJebolRaisesIsWordedByItsOwnSentence`; `spec/nav.allium` tells the
  two reasons apart).

## macOS

**Notifications need an app bundle.** `UNUserNotificationCenter.current()`
from a plain command-line binary throws at once: `bundleProxyForCurrentProcess
is nil`, because the process has no bundle identifier. *Forums, several
reports.*
- An Apple forums thread: a plain Swift CLI hits the exception immediately,
  and a reply suggests the older `CFUserNotificationDisplayNotice` instead,
  which is an alert, not a notification.
  https://developer.apple.com/forums/thread/724249
- A Stack Overflow answer: an `Info.plist` can be embedded in the binary with
  `CREATE_INFOPLIST_SECTION_IN_BINARY` to give it an identifier, and signing
  was not needed; or a helper app posts the notifications for the CLI over
  `NSDistributedNotificationCenter`.
  https://kaweah.freedombox.rocks/kiwix/stackoverflow.com_en_all_2023-11/a/36663079
- Apple DTS (Quinn) advises packaging a command-line tool in an app-like
  bundle, so the system treats it coherently.
  https://developer.apple.com/forums/thread/666105
- A Java-specific question about the same thing.
  https://kiwix.lema.org/stackoverflow.com_en_all_2023-11/questions/66144844/accessing-unusernotificationcenter-from-java-application
- Without an `NSRunLoop` a CLI may not get its notifications delivered at
  all (a commenter's view).
  https://kiwix.lema.org/stackoverflow.com_en_all_2023-11/questions/24460372/nsusernotificationcenter-defaultnotificationcenter-returns-nil

For JEBOL: a `java` process has the JDK's identity or none, never the app's,
so notifications on macOS come from a packaged `.app` (goal 2e). An embedded
`Info.plist` cannot be given to the JDK's own `java` binary.

**Location needs a bundle with `NSLocationUsageDescription`.** *First-party
for the key, forums for the CLI case.*
- Apple: macOS apps that use location must have `NSLocationUsageDescription`
  in their `Info.plist`.
  https://developer.apple.com/tutorials/data/documentation/bundleresources/information-property-list/nslocationusagedescription.md
- A CLI tool wrapped in an app bundle could get authorisation and print a
  position, after Apple DTS suggested trying it from an app template.
  https://developer.apple.com/forums/thread/769950
- A command-line tool is given an identifier generated from its path, which
  the location check does not recognise.
  https://developer.apple.com/forums/thread/124189
- Even a signed, notarised LaunchAgent with the keys in its bundle stayed at
  `notDetermined` on macOS 14.
  https://developer.apple.com/forums/thread/840513
- One Rust crate claims CoreLocation works from a CLI with no `Info.plist`,
  with the prompt going to the parent terminal: a single unverified source,
  against the threads above.
  https://docs.rs/crate/axterminator/0.9.1/source/src/context/location.rs
- How location services work on macOS, for background.
  https://eclecticlight.co/2026/06/01/what-location-services-do-in-macos/
- `CoreLocationCLI`, a third-party tool built with Xcode, as a reference for
  how others package it. https://github.com/craiga/corelocationcli

**Camera consent goes to the "responsible process".** *Bug reports and
blogs; Apple's documentation for the API.*
- Apple: call `AVCaptureDevice authorizationStatus(for:)`, then
  `requestAccess(for:completionHandler:)` to prompt.
  https://developer.apple.com/tutorials/data/documentation/avfoundation/requesting-authorization-to-capture-and-save-media.md
- TCC attributes a subprocess's camera request to the nearest signed ancestor
  with a bundle identity. If that app lacks `NSCameraUsageDescription`, no
  prompt appears and the status stays `notDetermined`.
  https://github.com/anthropics/claude-code/issues/82543
- Terminal asks for camera access on behalf of what it launches.
  https://cipherlog.blogspot.com/2020/12/macos-microphonecamera-permissions-and.html
- An app with camera access crashes when launched from Finder but works from
  Terminal: launched from Finder, it is itself responsible, and it needs the
  key. https://developer.apple.com/forums/thread/700714
- Apple's `tccutil reset` to prompt again.
  https://developer.apple.com/documentation/xcode/resetting-access-to-protected-resources-in-macos.md
- `NSCameraUsageDescription`.
  https://developer.apple.com/tutorials/data/documentation/bundleresources/information-property-list/nscamerausagedescription.md
- Briefcase pairs the camera's usage string with the entitlement
  `com.apple.security.device.camera`, which a sandboxed or hardened-runtime
  app may also need. 2h checks this for an ad-hoc-signed jpackage app.
  https://briefcase.beeware.org/en/v0.3.19/reference/platforms/macOS/xcode.html

**FFM and Objective-C.** *Mailing list and other languages' bindings; no Java
example found.*
- A Panama developer, December 2022: many macOS APIs need classes of one's
  own (a delegate), which `objc_allocateClassPair` and
  `objc_registerClassPair` make at run time, but there was no good way to
  link the Objective-C object to its Java one.
  https://mail.openjdk.org/pipermail/panama-dev/2022-December/018130.html
- LWJGL: `objc_msgSend` must be cast to the function type of each call shape,
  which in FFM is one downcall handle for each.
  https://javadoc.io/static/org.lwjgl/lwjgl/3.3.2/org/lwjgl/system/macosx/ObjCRuntime.html
- Dart passes a block pointer through to `objc_msgSend` for a completion
  handler. https://github.1485827954.workers.dev/dart-lang/native/issues/2904
- Racket wraps a host function as a block.
  https://docs.racket-lang.org/foreign/Objective-C_FFI.html
- JNA calling macOS APIs from Java, without blocks.
  https://dev.to/franzwong/using-java-native-access-jna-to-call-macos-api-2pi0

For JEBOL: a block is a struct (`isa`, flags, `invoke`, descriptor) whose
`invoke` would be an FFM upcall stub. Nothing found shows Java doing it,
which is why 2f's first Mac step is a probe.

## Windows

**A toast needs a Start-menu shortcut that carries the AppUserModelID.**
*First-party.*
- A shortcut carrying `System.AppUserModel.ID` must be installed in the
  Start screen, though not pinned. Without one, a desktop app cannot raise a
  toast, and `CreateToastNotifier` must be given the same ID each time.
  https://learn.microsoft.com/en-ie/windows/win32/shell/quickstart-sending-desktop-toast
  https://msdn.microsoft.com/en-US/library/hh802762
- Windows finds the shortcut by that property and takes the toast's name and
  icon from it. Microsoft advises creating it in the installer rather than in
  the app.
  https://learn.microsoft.com/en-us/windows/apps/design/shell/tiles-and-notifications/send-local-toast-desktop-cpp-wrl
- An unpackaged app also declares a toast activator CLSID on the shortcut,
  to be started by a click when it is not running.
  https://learn.microsoft.com/en-us/windows/apps/design/shell/tiles-and-notifications/send-local-toast-other-apps
- The property itself, and how an installer's `MsiShortcutProperty` table
  sets it. https://learn.microsoft.com/windows/win32/properties/props-system-appusermodel-id
  https://learn.microsoft.com/en-us/archive/blogs/windows_installer_team/windows-7-taskbar-support-with-the-msishortcutproperty-table
- AppUserModelIDs in general. https://learn.microsoft.com/windows/win32/shell/appids
- Chrome's own handling of shortcuts and IDs.
  https://chromium.googlesource.com/chromium/src/+/8d69b4b268f524e37ca262d25f72239184fcb9f4/docs/windows_shortcut_and_taskbar_handling.md

**How other frameworks give an app its ID.** *Framework docs, Microsoft, and
Firefox's tracker; searched 2026-11-18.*
- Electron: a Windows app needs a Start-menu shortcut with an AppUserModelID
  and a `ToastActivatorCLSID`. Electron's installers (Squirrel.Windows,
  through `electron-winstaller`) set the shortcut correctly by themselves.
  In development the app calls `app.setAppUserModelId()`.
  https://www.electronjs.org/docs/latest/tutorial/notifications
- The Windows App SDK: for an unpackaged WinForms, WPF or console app,
  `AppNotificationManager.Register()` sets up the COM registration that lets
  Windows start the app when a notification is clicked, and no AUMID needs
  configuring by hand. Register the `NotificationInvoked` handler before
  calling `Register()`, and unregister when the app ends.
  https://learn.microsoft.com/en-us/windows/windows-app-sdk/api/winrt/microsoft.windows.appnotifications.appnotificationmanager.register
  https://learn.microsoft.com/da-dk/windows/apps/develop/notifications/app-notifications/app-notifications-winforms
  https://learn.microsoft.com/windows/apps/develop/notifications/app-notifications/app-notifications-quickstart?tabs=cs
- Other unpackaged apps register in the registry, under
  `Software\Classes\AppUserModelId\<AUMID>`, with a display name, an icon and
  a COM activator's GUID. The page seen shows `HKEY_LOCAL_MACHINE`; whether a
  per-user app may use `HKEY_CURRENT_USER` was not confirmed. Without a
  shortcut, the icon comes from the shortcut, then the process's resources,
  then Windows's default. An AUMID over 129 characters breaks scheduled
  toasts.
  https://learn.microsoft.com/en-us/windows/apps/design/shell/tiles-and-notifications/send-local-toast-other-apps
- Firefox, for its installed and portable builds, writes the registry at run
  time with the icon, the display name and the COM server, so its
  notifications no longer depend on a shortcut.
  https://bugzilla.mozilla.org/show_bug.cgi?id=1774082
- Flutter has no notifications of its own on the desktop. Packages such as
  `flutter_desktop_notifications` give an unpackaged app a call that
  registers an AUMID with a display name.
  https://pub.dev/packages/flutter_desktop_notifications

**Location outside an app container.** *First-party API docs; forums and
vendors for the unpackaged case, since no Microsoft page covers it.*
- `Geolocator`, `RequestAccessAsync` and `GetGeopositionAsync`; the app must
  be in the foreground and declare the Location capability, which only a
  packaged app has.
  https://learn.microsoft.com/en-us/uwp/api/Windows.Devices.Geolocation.Geolocator
- From a Win32 app, `RequestAccessAsync` threw `0x8007109A`: it must be
  called in the context of an app container.
  https://windows-hexerror.linestarve.com/q/so39039797-using-uwp-geolocation-api-with-win32-application-on-windows-10
  https://windows-hexerror.linestarve.com/q/so52023123-how-to-use-geolocatorrequestaccessasync-in-wpf-desktop-app
- Windows 11 24H2: unpackaged apps have no per-app location permission. They
  share one switch, "let desktop apps access your location", and the system
  prompts the first time an app asks.
  https://www.barco.com/en/support/knowledge-base/clickshare-issue-0005-351
  https://www.barco.com/en/support/knowledge-base/can-i-use-app-id-to-manage-location-services-for-the-clickshare-app-on-windows-11-24h2-or-later
  https://yomotherboard.com/?p=92508

**The camera** (Media Foundation) was not searched on 2026-11-18; 2h's plan
does it.

## Linux

**GeoClue authorises by desktop ID.** *Its manual.*
- `geoclue.conf`: `[agent] whitelist=` names the agents it trusts
  (`gnome-shell` among them). An `[app-id]` section, keyed by the desktop ID
  without `.desktop`, with `allowed=true` lets an app skip the agent; `system`
  and `users` say whether it is a system component and which UIDs it serves.
  The samples have `[firefox]` and `[epiphany]`.
  https://man.archlinux.org/man/geoclue.5
  https://manpages.ubuntu.com/manpages/noble/man5/geoclue.5.html
- A fixed position for GeoClue through `gpsfake` and `gpsd`.
  https://discourse.gnome.org/t/use-gpsfake-and-gpsd-to-create-a-fixed-geographical-location-for-geoclue/6535

**Mozilla's location service is gone.** *Mozilla, distributions.*
- Mozilla retired the Mozilla Location Service, with lookups ending on 12 June
  2024. https://discourse.mozilla.org/t/retiring-the-mozilla-location-service/128693
- Fedora's and GNOME's tracking.
  https://bugzilla.redhat.com/show_bug.cgi?id=2284621
  https://gitlab.gnome.org/GNOME/gnome-control-center/-/issues/2959
- On Ubuntu, GeoClue lookups then fail, and the workaround offered is a
  fixed position.
  https://wikipedia.jakami.de/content/askubuntu.com_en_all_2025-12/questions/1537682/geoclue-3863-failed-to-query-location-query-location-soup-error-not-found
- BeaconDB is a drop-in replacement for the same request format, and Debian's
  packages use it; upstream supports only Google's service otherwise.
  https://alternativeto.net/software/beacondb/about
  https://www.linuxfromscratch.org/blfs/view/12.4/basicnet/geoclue2.html
  https://lists.opensuse.org/archives/list/bugs@lists.opensuse.org/message/NNVHDT3F3XILDHZ2LQXYM6ZNCCMPRXNV/attachment/2/attachment.html
  https://gitlab.archlinux.org/archlinux/packaging/packages/geoclue/-/issues/1

For JEBOL: a desktop with no GPS and no Wi-Fi database may get no fix at all.

**Notifications** go through `org.freedesktop.Notifications` on the session
bus, which shows what any client sends and needs no identity. **V4L2**, for
the camera, is the kernel's interface; `v4l2loopback` gives tests a device to
write into. Neither was searched on 2026-11-18, since neither was in doubt;
2f's and 2h's plans read the specifications.

## The browser

**Notifications.** *MDN and the WHATWG list.*
- Ask for permission in response to a user gesture.
  https://ligm.univ-eiffel.fr/~forax/MDN/developer.mozilla.org/en-US/docs/Web/API/Notification/requestPermission_static.html
  https://ligm.univ-eiffel.fr/~forax/MDN/developer.mozilla.org/en-US/docs/Web/API/Notifications_API.html
- In 2013, Chrome was unlikely to support `requestPermission()` in workers,
  since it allows the call only in response to user input.
  https://lists.whatwg.org/pipermail/whatwg-whatwg.org/2013-October/083546.html
  https://lists.w3.org/Archives/Public/public-whatwg-archive/2013Oct/0339.html
- Firefox 41 gave workers notifications, but `requestPermission` from a
  worker will not prompt. https://bugzilla.mozilla.org/show_bug.cgi?id=916893

For JEBOL, whose interpreter runs in a worker: the page asks and shows,
through `PageRoundTrip`. Whether geolocation and `getUserMedia` exist in a
worker was not found, and does not matter, since the permission prompts are
the window's anyway.

**Test support over WebDriver.** *Firefox's release notes; Chrome
unconfirmed.*
- Firefox 127 has BiDi `permissions.setPermission`, and Firefox 139 has
  `emulation.setGeolocationOverride`, whose reset Firefox 148 aligned with the
  specification.
  https://fxdx.dev/firefox-webdriver-newsletter-127/
  https://fxdx.dev/firefox-webdriver-newsletter-139/
  https://bugzilla.mozilla.org/show_bug.cgi?id=1875065
- No Chrome version for either BiDi command was found. CDP has
  `Emulation.setGeolocationOverride` and `Browser.grantPermissions`, which
  Selenium reaches through `executeCdpCommand`.
  https://webscraping.ai/faq/selenium-webdriver/how-can-i-handle-geolocation-and-permissions-with-selenium-webdriver
  https://www.selenium.dev/ja/documentation/webdriver/bidi/cdp/
  https://cdn.jsdelivr.net/npm/chromium-bidi@15.0.0/README.md
- WebKit has open bugs for both.
  https://www2.webkit.org/show_bug.cgi?id=265621
  https://113070-bug-28966-attachments.webkit.org/show_bug.cgi?id=292558

**Fake cameras** in the browsers (`--use-fake-device-for-media-stream`,
`--use-fake-ui-for-media-stream` in Chrome; `media.navigator.streams.fake`,
`media.navigator.permission.disabled` in Firefox) are known flags, not
searched on 2026-11-18. 2h checks them.

## Android

Not searched: the APIs were not in doubt. `POST_NOTIFICATIONS` is a runtime
permission from API 33; `LocationManager.getCurrentLocation` is from API 30
and needs no Google Play services; Camera2 needs no `androidx` dependency;
the emulator has `adb emu geo fix` and virtual cameras. Each plan reads
Android's own documentation.

## Packaging: Android's signing, as Flutter does it

*Flutter's own guide, with CI vendors' docs; searched 2026-11-18.*
- The developer makes an upload keystore with `keytool -genkey -v -keystore
  upload-keystore.jks -keyalg RSA -keysize 2048 -validity 10000 -alias
  upload`, and writes `android/key.properties` naming its `storeFile`,
  `storePassword`, `keyAlias` and `keyPassword`. The app's Gradle build loads
  that file into a `release` signing config. `flutter build appbundle` makes
  the bundle for the Play Store, and `flutter build apk --release` an APK.
  https://docs.flutter.dev/deployment/android
- The keystore and `key.properties` stay out of version control. CI decodes
  the keystore from a secret and writes `key.properties` at build time.
  https://docs.codemagic.io/flutter-code-signing/android-code-signing/
  https://docs.shorebird.dev/flutter-concepts/releasing-flutter-apps/android/
- Without Play App Signing, the same key is needed for every update, so it
  must be backed up. A `keyAlias` that does not match the keystore's is the
  common mistake. *Community guides.*
  https://www.desarrollolibre.net/blog/flutter/steps-to-generate-an-apk-and-abb-signed-in-release-mode-in-flutter-using-vsc
- Older guides left release builds on the debug key as a placeholder; the
  sources disagree on whether today's template still does.

## Packaging: jpackage

- jpackage builds only on the platform it targets: there is no cross-platform
  support. *Oracle's guide and the manual.*
  https://docs.oracle.com/en/java/javase/25/jpackage/packaging-overview.html
  https://man.archlinux.org/man/jpackage-openjdk26.1.en.raw
- Neither jpackage's Windows options (`--win-menu`, `--win-shortcut`, ...)
  nor anything found sets `System.AppUserModel.ID` on its shortcut. The
  JDK's own launchers had the same gap. *The JDK's bug tracker.*
  https://bugs.openjdk.org/browse/JDK-8188247
- A jpackage app image has a `Contents/Info.plist` that can hold further
  keys. In two-phase packaging, a signed image keeps its signature only if
  what jpackage adds does not break it, so the usage strings go in before
  signing. https://bugs.openjdk.org/browse/JDK-8351192
- pinpit packages for other platforms from Linux without jpackage.
  https://github.com/mobanisto/pinpit-gradle-plugin
