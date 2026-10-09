// Mixtape landing page configuration — the ONLY place the APK download URL lives.
//
// apkDownloadUrl: public HTTPS URL of the signed release APK.
//   Leave "" until the APK is actually uploaded; the Download button stays disabled
//   and an explanatory message is shown instead.
//
//   Recommended (GitHub Release asset with a fixed file name, always points at the latest release):
//     "https://github.com/kanakmegha/Mixtape/releases/latest/download/Mixtape.apk"
//   See docs/RELEASE.md -> "Publishing the APK".
window.MIXTAPE_CONFIG = {
  apkDownloadUrl: "https://github.com/kanakmegha/Mixtape/releases/latest/download/Mixtape.apk",
  androidPackage: "com.example.privatemusiclibrary",
};
