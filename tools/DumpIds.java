import java.io.PrintStream;

/**
 * Prints the numeric resource IDs of the framework attributes and resources
 * referenced by our hand-built AndroidManifest.xml. Compiled and run against
 * android.jar so the values are authoritative rather than guessed.
 */
public class DumpIds {
    public static void main(String[] args) throws Exception {
        PrintStream o = System.out;
        // attribute resource ids (android:<name>)
        o.println("attr.versionCode="        + android.R.attr.versionCode);
        o.println("attr.versionName="        + android.R.attr.versionName);
        o.println("attr.minSdkVersion="      + android.R.attr.minSdkVersion);
        o.println("attr.targetSdkVersion="   + android.R.attr.targetSdkVersion);
        o.println("attr.label="              + android.R.attr.label);
        o.println("attr.icon="               + android.R.attr.icon);
        o.println("attr.theme="              + android.R.attr.theme);
        o.println("attr.hardwareAccelerated="+ android.R.attr.hardwareAccelerated);
        o.println("attr.name="               + android.R.attr.name);
        o.println("attr.exported="           + android.R.attr.exported);
        o.println("attr.configChanges="      + android.R.attr.configChanges);
        o.println("attr.screenOrientation="  + android.R.attr.screenOrientation);
        // framework resources we reference
        o.println("res.icon="  + android.R.drawable.sym_def_app_icon);
        o.println("res.theme=" + android.R.style.Theme_Black_NoTitleBar_Fullscreen);
    }
}
