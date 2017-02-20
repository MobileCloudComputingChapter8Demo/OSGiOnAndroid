package edu.asu.snac.offloading;

import android.app.Activity;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.Formatter;
import android.text.method.ScrollingMovementMethod;
import android.widget.TextView;

import org.apache.commons.io.FileUtils;
import org.apache.felix.main.Main;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.util.Date;

import static org.apache.felix.main.AutoProcessor.AUTO_DEPLOY_ACTION_PROPERTY;
import static org.apache.felix.main.AutoProcessor.AUTO_DEPLOY_INSTALL_VALUE;
import static org.apache.felix.main.AutoProcessor.AUTO_DEPLOY_START_VALUE;

public class MainActivity extends Activity {
    public static File pathLoad;
    public static File pathWatch;
    public static File pathRuntimeCache;
    private TextView text;

    // set out & err
    private void redirectStd() throws UnsupportedEncodingException {
        PrintStream ps = new PrintStream(new OutputStream() {
            @Override
            public void write(final int oneByte) throws IOException {
                // in UI thread
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        text.append(String.valueOf((char) oneByte));
                        // append timestamp after every line
                        if (String.valueOf((char) oneByte).equals("\n")) {
                            text.append(new Date().toString() + "\n----------------------\n");
                        }
                    }
                });
            }
        }, true);
        System.setOut(ps);
        System.setErr(ps);
    }

    // sync print among threads
    private void printOneLineLog(String str) {
        synchronized (System.in) {
            System.out.println(str);
        }
    }

    // prepare osgi directories
    private void prepareDirs() throws IOException {
        // path root
        File pathRoot = getExternalFilesDir(null);
        printOneLineLog("osgi directory : " + pathRoot.getCanonicalPath());
        // path auto-load
        pathLoad = new File(pathRoot, "bundle-deploy-dir");
        if (pathLoad.mkdirs()) {
            printOneLineLog("assure auto-load directory " + pathLoad.getCanonicalPath() + " : success");
        } else {
            printOneLineLog("assure auto-load directory " + pathLoad.getCanonicalPath() + " : fail - already exist?");
        }
        // path file install
        pathWatch = new File(pathRoot, "bundle-watch-dir");
        if (pathWatch.mkdirs()) {
            printOneLineLog("assure file-install directory " + pathWatch.getCanonicalPath() + " : success");
        } else {
            printOneLineLog("assure file-install directory " + pathWatch.getCanonicalPath() + " : fail - already exist?");
        }
        // path runtime-cache
        pathRuntimeCache = new File(pathRoot, "bundle-cache-dir");
        if (pathRuntimeCache.mkdirs()) {
            printOneLineLog("prepare runtime-cache directory " + pathRuntimeCache.getCanonicalPath() + " : success");
        } else {
            printOneLineLog("prepare runtime-cache directory " + pathRuntimeCache.getCanonicalPath() + " : fail - already exist?");
        }
        // clean runtime-cache
        FileUtils.deleteDirectory(pathRuntimeCache);
        if (pathRuntimeCache.mkdirs()) {
            printOneLineLog("assure runtime-cache directory " + pathRuntimeCache.getCanonicalPath() + " : success");
        } else {
            printOneLineLog("assure runtime-cache directory " + pathRuntimeCache.getCanonicalPath() + " : fail - already exist?");
        }
        // clean dir check
        String[] cacheContent = pathRuntimeCache.list();
        if (cacheContent != null) {
            for (String content : cacheContent) {
                printOneLineLog("runtime-cache directory is not clean " + content);
            }
        }
    }

    // get phone ip address
    private String getLocalIpAddress() {
        WifiManager wm = (WifiManager) getSystemService(WIFI_SERVICE);
        String ip = Formatter.formatIpAddress(wm.getConnectionInfo().getIpAddress());
        return ip;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // get text area obj
        text = (TextView) findViewById(R.id.log);
        text.setMovementMethod(new ScrollingMovementMethod());

        // redirect print to screen
        try {
            redirectStd();
        } catch (UnsupportedEncodingException e) {
            text.setText(e.getMessage());
            e.printStackTrace();
        }

        // start osgi thread
        new Thread(new Runnable() {
            @Override
            public void run() {
                // pass key-value to osgi framework
                System.setProperty(AUTO_DEPLOY_ACTION_PROPERTY, TextUtils.join(",", new String[]{AUTO_DEPLOY_INSTALL_VALUE, AUTO_DEPLOY_START_VALUE}));
                System.setProperty("org.osgi.framework.system.packages.extra", "org.json");
//                System.setProperty("gosh.args", "--nointeractive");
                System.setProperty("osgi.shell.telnet.ip", "0.0.0.0");
//            System.setProperty("org.osgi.framework.storage.clean", "onFirstInit");
                System.setProperty("felix.fileinstall.bundles.new.start", String.valueOf(false));

                try {
                    prepareDirs();

                    try {
//                        System.setProperty("user.dir", pathRoot.getCanonicalPath());
                        System.setProperty("felix.fileinstall.dir", pathWatch.getCanonicalPath());

                        printOneLineLog("starting osgi .. please try 'TELNET " + getLocalIpAddress() + " 6666'");
                        Main.main(new String[]{Main.BUNDLE_DIR_SWITCH, pathLoad.getCanonicalPath(), pathRuntimeCache.getCanonicalPath()});
                    } catch (Exception e) {
                        printOneLineLog(e.getMessage());
                        e.printStackTrace();
                    }
                } catch (IOException e) {
                    printOneLineLog(e.getMessage());
                    e.printStackTrace();
                }
            }
        }).start();
    }
}
