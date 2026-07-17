import com.android.apksig.ApkSigner;

import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Signs an APK with APK Signature Scheme v1 (JAR) and v2 using the apksig
 * library, replacing the SDK's `apksigner` tool.
 *
 * args: <keystore.p12> <storepass> <in.apk> <out.apk>
 */
public class Signer {
    public static void main(String[] args) throws Exception {
        String ksPath = args[0];
        char[] pw = args[1].toCharArray();
        File in = new File(args[2]);
        File out = new File(args[3]);

        KeyStore ks = KeyStore.getInstance("PKCS12");
        FileInputStream fis = new FileInputStream(ksPath);
        ks.load(fis, pw);
        fis.close();

        String alias = ks.aliases().nextElement();
        PrivateKey key = (PrivateKey) ks.getKey(alias, pw);
        Certificate[] chain = ks.getCertificateChain(alias);
        List<X509Certificate> certs = new ArrayList<X509Certificate>();
        for (Certificate c : chain) certs.add((X509Certificate) c);

        ApkSigner.SignerConfig signerConfig =
                new ApkSigner.SignerConfig.Builder("NOVA", key, certs).build();

        ApkSigner signer = new ApkSigner.Builder(Collections.singletonList(signerConfig))
                .setInputApk(in)
                .setOutputApk(out)
                .setMinSdkVersion(24)
                .setV1SigningEnabled(false) // v1 handled by jarsigner (JDK)
                .setV2SigningEnabled(true)
                .build();
        signer.sign();
        System.out.println("signed -> " + out.getAbsolutePath());
    }
}
