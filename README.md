Shelter
===

Shelter is a Free and Open-Source (FOSS) app that leverages the "Work Profile" feature of Android to provide an isolated space that you can install or clone apps into.

Downloads
===

- [F-Droid](https://f-droid.org/app/net.typeblog.shelter) (Signed by F-Droid)
- Custom F-Droid Repository (Signed by PeterCxy, contains latest development versions):
  - [Click here](fdroidrepos://fdroid.typeblog.net?fingerprint=1A7E446C491C80BC2F83844A26387887990F97F2F379AE7B109679FEAE3DBC8C) to add from your phone
  - Or scan the following QR-code:  
  ![](fdroid_custom_repo.png)
  - Or setup manually:
    - Url: https://fdroid.typeblog.net
    - Fingerprint: `1A 7E 44 6C 49 1C 80 BC 2F 83 84 4A 26 38 78 87 99 0F 97 F2 F3 79 AE 7B 10 96 79 FE AE 3D BC 8C`

You cannot switch between versions listed above that have different signature without uninstalling Shelter first.

Features
===

- Installing apps inside a work profile for isolation
- "Freeze" apps inside the work profile to prevent them from running or being woken up when you are not actively using them
- Installing two copies of the same app on the same device

Discussion & Support
===

- [Mailing List](https://lists.sr.ht/~petercxy/shelter)
- Matrix Chat Room: #shelter:neo.angry.im

__The GitHub Issue list and pull requests are not checked regularly. Please use the mailing list instead.__

Caveats & Known Issues
===

- Some caveats and known issues are discussed during the setup process of Shelter. __Please read through text in the setup wizard carefully__.
- Shelter is only as safe as the Work Profile implementation of the Android OS you are using. For details, see <https://support.google.com/work/android/answer/6191949?hl=en>

State of the Project, Feature Requests, etc.
===

Since Shelter simply makes use of the Work Profile APIs exposed by Android, there is a limited set of features that are possible to implement via the app. As we do not intend on leveraging (or "abusing") adb privileges, the features of Shelter can only be a strict subset of the exposed, unprivileged APIs.

As a result, we do not intend on adding a lot of new features to Shelter going forward, unless there is to be big changes in the capabilities of work profile APIs. Shelter is currently in an effective **maintenance mode**. Nevertheless, the author is still committed to regularly **adapting Shelter to all new Android versions as soon as possible after they are released** -- this includes upgrading the target SDK level, adapting to any new features or restrictions introduced by the new Android version, updating all dependencies, and so on. The author still relies on Shelter for his daily life, so Shelter will **not** become abandonware in the forseeable future.

Contributing
===

- [Weblate](https://weblate.typeblog.net/projects/shelter/shelter/) for contributing translations
- Sponsor me on [Patreon](https://www.patreon.com/PeterCxy)

<a href="http://weblate.typeblog.net/engage/shelter/?utm_source=widget">
  <img src="http://weblate.typeblog.net/widgets/shelter/-/shelter/multi-auto.svg" alt="Translation status" />
</a>

Uninstalling
===

To uninstall Shelter, please delete the work profile first in Settings -> Accounts, and then uninstall the Shelter app normally.
