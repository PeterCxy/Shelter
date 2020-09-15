![Shelter](https://cgit.typeblog.net/Shelter/plain/art/ic_launcher_egg-web.png)

<a href='https://play.google.com/store/apps/details?id=net.typeblog.shelter&pcampaignid=MKT-Other-global-all-co-prtnr-py-PartBadge-Mar2515-1'><img alt='Get it on Google Play' src='https://play.google.com/intl/en_us/badges/images/generic/en_badge_web_generic.png' height="80"/></a>
<a href="https://f-droid.org/app/net.typeblog.shelter"><img src="https://f-droid.org/badge/get-it-on.png" alt="Get it on F-Droid" height="80"></a>

<a href="http://weblate.typeblog.net/engage/shelter/?utm_source=widget">
  <img src="http://weblate.typeblog.net/widgets/shelter/-/shelter/multi-auto.svg" alt="Translation status" />
</a>

Note: main repository: <https://cgit.typeblog.net/Shelter/>. __The GitHub repository is only a mirror__. I do not guarantee the GitHub repository will be up-to-date.

For bug reports and patches (pull requests), please use the following contact methods:

- Mailing List (SourceHut): <https://lists.sr.ht/~petercxy/shelter>
- Matrix Chat Room: #shelter:neo.angry.im

For translations, please use our Weblate interface at <https://weblate.typeblog.net/projects/shelter/shelter/> to submit new translated strings for Shelter.

On GitHub, any new issues or pull requests will not be read or accepted. Please use the contacts listed above.

Note: The F-Droid version is automatically built and signed by the F-Droid server on each update of Shelter. The build is not managed by the author and could lag behind the updates from Play Store and this repository for several days due to high server load.


Shelter - Isolate your Big Brother Apps / Multiple Accounts
===

Shelter is a Free and Open-Source (FOSS) app that leverages the "Work Profile" feature of Android to provide an isolated space that you can install or clone apps into.

Shelter comes with absolutely no advertisement / statistics / tracking SDKs bundled with it. All source code is available in this public Git repository and the sources are licensed under WTFPL.

This app depends on your Android system's implementation of Work Profile. Some vendor / custom ROMs may have a broken implementation that may cause crashes and even bricking of your device. One such example is MIUI from Xiaomi. I currently provide no support for such ROMs because I personally do not own any of these devices. If you are running Shelter on these ROMs, you are on your own. If any developer out there own these devices and could make Shelter run on these ROMs, please send pull requests and I'll be happy to merge them.

Features / Use Cases
===

1. Run "Big Brother" apps inside the isolated profile so they cannot access your data / files outside the profile
2. "Freeze" (disable) background-heavy, tracking-heavy or seldom-used apps when you don't need them. This is especially true if you use apps from Chinese companies like Baidu, Alibaba, Tencent.
3. Clone apps to use two accounts on one device

Known Issues
===

1. "Split APKs" (APKs that consist of multiple sub-packages) cannot be cloned properly. This includes a lot of applications on the Play Store (e.g. WhatsApp). When possible, use "Install APK into Shelter" from the menu instead.
2. File Shuttle is not supported on Android 10
3. Shelter must be installed in INTERNAL STORAGE, otherwise the initialization process will fail.
4. You have to click a notification in order to finish the initialization process due to the limitations of background apps introduced since Android 10. When initializing, please make sure you are not in "Do Not Disturb" mode.

Caveats
===

Shelter is __not__ a full sandbox implementation. It cannot protect you from:

1. Security bugs of the Android system or Linux kernel
2. Backdoors installed in your Android system (so please use an open-source ROM if you are concerned about this)
3. Backdoors installed into the firmwares (no way to work around this)
4. Any other bugs or limitations imposed by the Android system. (i.e. If Android chooses to expose some information into the work profile, there is nothing I could do about it)

For information that may still be exposed to the work profile, please refer to this support article by Google: <https://support.google.com/work/android/answer/6191949?hl=en>. Note that though the section "what data about my device is visible to my organization" is about the information visible to __administrator__, not necessarily every application, the fact that those information are not totally isolated is still a big caveat to the work profile feature.

Also, Shelter cannot create more than 1 work profile on one Android device, and cannot co-exist with any other apps that manages a Work Profile. This is due to the limitations of the Android system, and I can do nothing about this.

FAQS
===

**Q**: Why not use Island by OasisFeng, the creator of Greenify?  
**A**: ~~Simply because it is not an FOSS app and it bundles with non-free SDKs. Note that this doesn't necessarily mean that Island has anti-features like tracking (and I don't think it has either), it's just that I wrote Shelter as an FOSS replacement of it. There is no other reason why one would prefer Shelter over Island except for this one.~~ This is no longer true. Island is now also FOSS, but I am keeping this project for my stupid affection for WTFPL.

**Q**: Why does Shelter always run in background?  
**A**: Please try removing Shelter from "Recent Apps" every time you close it. If it still persists in your notifications and eating up battery, you might have encountered a bug. Please file a bug report.

**Q**: How do I uninstall Shelter from my device?  
**A**: 1) Go to Settings -> Accounts to remove the work profile; 2) Go to Settings -> Security -> Advanced -> Device admin apps to remove Shelter from Device Admin apps; 3) Uninstall Shelter normally.

**Q**: If I encounter bugs, how do I report them?  
**A**: Please send a message to our mailing list <https://lists.sr.ht/~petercxy/shelter>. You may also join our Matrix chat room at #shelter:neo.angry.im. 

**Q**: How do I support the project?  
**A**: You can submit issues if you find a bug or have an idea about features of Shelter; you may also contribute code to this project if you can code; providing translations is also welcomed. If you have some extra money lying around, you can also [support me on Patreon](https://www.patreon.com/PeterCxy).
