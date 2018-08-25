![Shelter](https://git.angry.im/PeterCxy/Shelter/raw/branch/master/app/src/main/ic_launcher_shelter-web.png)

Shelter - Isolate your Big Brother Apps / Multiple Accounts
===

Shelter is a Free and Open-Source (FOSS) app that leverages the "Work Profile" feature of Android to provide an isolated space that you can install or clone apps into.

Shelter comes with absolutely no advertisement / statistics / tracking SDKs bundled with it. All source code is available in this public Git repository and the sources are licensed under WTFPL.

This app depends on your Android system's implementation of Work Profile. Some vendor / custom ROMs may have a broken implementation that may cause crashes and even bricking of your device. One such example is MIUI from Xiaomi. I currently provide no support for such ROMs because I personally do not own any of these devices. If you are running Shelter on these ROMs, you are on your own. If any developer out there own these devices and could make Shelter run on these ROMs, please send pull requests and I'll be happy to merge them.

Features / Use Cases
===

1. Run "Big Brother" apps inside the isolated profile so they cannot access your data outside the profile
2. "Freeze" (disable) background-heavy or seldom-used apps when you don't need them. This is especially true if you use apps from Chinese companies like Baidu, Alibaba, Tencent.
3. Clone apps to use two accounts on one device

Caveats
===

Shelter is not a full sandbox implementation. It cannot protect you from:

1. Security bugs of the Android system or Linux kernel
2. Backdoors installed in your Android system (so please use an open-source ROM if you are concerned about this)
3. Backdoors installed into the firmwares (no way to work around this)
4. Any other bugs or limitations imposed by the Android system.

Also, Shelter cannot create more than 1 work profile on one Android device, and cannot co-exist with any other apps that manages a Work Profile. This is due to the limitations of the Android system, and I can do nothing about this.

FAQS
===

**Q**: Why not use Island by OasisFeng, the creator of Greenify?
**A**: Simply because it is not an FOSS app and it bundles with non-free SDKs. Note that this doesn't necessarily mean that Island has anti-features like tracking (and I don't think it has either), it's just that I wrote Shelter as an FOSS replacement of it. There is no other reason why one would prefer Shelter over Island except for this one.

**Q**: How do I uninstall Shelter from my device?
**A**: 1) Go to Settings -> Accounts to remove the work profile; 2) Go to Settings -> Security -> Advanced -> Device admin apps to remove Shelter from Device Admin apps; 3) Uninstall Shelter normally.

**Q**: If I encounter bugs, how do I report them?
**A**: You could file an issue on either the main repository at <https://git.angry.im/PeterCxy/Shelter> or the mirror repository at <https://github.com/PeterCxy/Shelter>.
