package com.klipstudyo.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final int FILE_REQ = 11;
    private WebView web;
    private ValueCallback<Uri[]> fileCb;
    private File tmpFile;
    private FileOutputStream tmpOut;
    private String outName, outMime;
    private Uri lastUri;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        web.setWebViewClient(new WebViewClient());
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams p) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = cb;
                ArrayList<String> mimes = new ArrayList<>();
                for (String a : p.getAcceptTypes()) {
                    if (a == null) continue;
                    for (String m : a.split(",")) {
                        m = m.trim();
                        if (m.contains("/")) mimes.add(m);
                    }
                }
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                if (mimes.size() == 1) i.setType(mimes.get(0));
                else {
                    i.setType("*/*");
                    if (!mimes.isEmpty()) i.putExtra(Intent.EXTRA_MIME_TYPES, mimes.toArray(new String[0]));
                }
                i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, p.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
                try {
                    startActivityForResult(Intent.createChooser(i, "Seç"), FILE_REQ);
                } catch (Exception e) {
                    fileCb = null;
                    return false;
                }
                return true;
            }
        });
        web.addJavascriptInterface(new Bridge(), "Android");
        web.loadUrl("file:///android_asset/index.html");
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != FILE_REQ || fileCb == null) return;
        Uri[] out = null;
        if (res == RESULT_OK && data != null) {
            ClipData cd = data.getClipData();
            if (cd != null && cd.getItemCount() > 0) {
                out = new Uri[cd.getItemCount()];
                for (int i = 0; i < out.length; i++) out[i] = cd.getItemAt(i).getUri();
            } else if (data.getData() != null) out = new Uri[]{data.getData()};
        }
        fileCb.onReceiveValue(out);
        fileCb = null;
    }

    @Override
    public void onBackPressed() {
        new AlertDialog.Builder(this)
                .setMessage("Uygulamadan çıkılsın mı? Kaydedilmemiş proje kaybolur.")
                .setPositiveButton("Çık", (d, w) -> finish())
                .setNegativeButton("Vazgeç", null)
                .show();
    }

    private void copy(InputStream in, OutputStream out) throws Exception {
        byte[] buf = new byte[1 << 16];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        in.close();
        out.close();
    }

    private String saveToGallery() throws Exception {
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues v = new ContentValues();
            v.put(MediaStore.Video.Media.DISPLAY_NAME, outName);
            v.put(MediaStore.Video.Media.MIME_TYPE, outMime);
            v.put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/KlipStudyo");
            v.put(MediaStore.Video.Media.IS_PENDING, 1);
            Uri u = getContentResolver().insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v);
            if (u == null) throw new Exception("Galeriye eklenemedi");
            copy(new FileInputStream(tmpFile), getContentResolver().openOutputStream(u));
            v.clear();
            v.put(MediaStore.Video.Media.IS_PENDING, 0);
            getContentResolver().update(u, v, null, null);
            lastUri = u;
            tmpFile.delete();
            return "Galeri › Movies/KlipStudyo/" + outName;
        } else {
            File dir = getExternalFilesDir(Environment.DIRECTORY_MOVIES);
            if (dir != null && !dir.exists()) dir.mkdirs();
            File f = new File(dir, outName);
            copy(new FileInputStream(tmpFile), new FileOutputStream(f));
            tmpFile.delete();
            MediaScannerConnection.scanFile(this, new String[]{f.getAbsolutePath()}, new String[]{outMime}, null);
            lastUri = FileProvider.getUriForFile(this, getPackageName() + ".fp", f);
            return f.getAbsolutePath();
        }
    }

    class Bridge {
        @JavascriptInterface
        public boolean begin(String name, String mime) {
            try {
                outName = name.replaceAll("[^A-Za-z0-9._-]", "_");
                outMime = mime;
                tmpFile = new File(getCacheDir(), "export.tmp");
                tmpOut = new FileOutputStream(tmpFile);
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public boolean append(String b64) {
            try {
                tmpOut.write(Base64.decode(b64, Base64.DEFAULT));
                return true;
            } catch (Exception e) {
                return false;
            }
        }

        @JavascriptInterface
        public String finish() {
            try {
                tmpOut.close();
                tmpOut = null;
                return saveToGallery();
            } catch (Exception e) {
                return "HATA: " + e.getMessage();
            }
        }

        @JavascriptInterface
        public void share() {
            runOnUiThread(() -> {
                if (lastUri == null) return;
                Intent i = new Intent(Intent.ACTION_SEND);
                i.setType(outMime);
                i.putExtra(Intent.EXTRA_STREAM, lastUri);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(i, "Paylaş"));
            });
        }

        @JavascriptInterface
        public void toast(String m) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, m, Toast.LENGTH_LONG).show());
        }
    }
}
