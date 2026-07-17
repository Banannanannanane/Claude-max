import com.android.apksig.ApkVerifier;

import java.io.File;

/** Verifies an APK's signatures (v1/v2) with apksig, mirroring `apksigner verify`. */
public class Verifier {
    public static void main(String[] args) throws Exception {
        ApkVerifier verifier = new ApkVerifier.Builder(new File(args[0]))
                .setMinCheckedPlatformVersion(24)
                .build();
        ApkVerifier.Result r = verifier.verify();
        System.out.println("verified      = " + r.isVerified());
        System.out.println("usingV1Scheme = " + r.isVerifiedUsingV1Scheme());
        System.out.println("usingV2Scheme = " + r.isVerifiedUsingV2Scheme());
        for (ApkVerifier.IssueWithParams e : r.getErrors())
            System.out.println("ERROR: " + e);
        for (ApkVerifier.IssueWithParams w : r.getWarnings())
            System.out.println("WARN:  " + w);
        for (ApkVerifier.Result.V1SchemeSignerInfo s : r.getV1SchemeSigners())
            for (ApkVerifier.IssueWithParams e : s.getErrors())
                System.out.println("V1 ERROR: " + e);
        for (ApkVerifier.Result.V2SchemeSignerInfo s : r.getV2SchemeSigners())
            for (ApkVerifier.IssueWithParams e : s.getErrors())
                System.out.println("V2 ERROR: " + e);
        System.exit(r.isVerified() ? 0 : 2);
    }
}
