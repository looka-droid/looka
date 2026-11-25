package ru.looka.app;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

/**
 * Приложение LOOKA — это сам сайт, открытый внутри приложения.
 *
 * Почему так, а не «настоящее» приложение: весь LOOKA уже написан как одна
 * веб-страница, и переписывать его заново на Kotlin — это месяцы работы и
 * вторая копия, которую пришлось бы чинить дважды. Обёртка даёт значок на
 * экране, полноэкранный вид без адресной строки и место в RuStore, а
 * содержимое остаётся одно и обновляется само, когда вы меняете сайт.
 */
public class MainActivity extends AppCompatActivity {

    /** Адрес сайта. Если смените домен — меняется только эта строка. */
    private static final String HOME = "https://looka-droid.github.io/looka/";

    /** Свои хосты: по ним ходим внутри приложения. Оплата тоже остаётся внутри. */
    private static final String[] INSIDE = {
            "looka-droid.github.io",
            "looka-api.annabredihina25.workers.dev",
            "yookassa.ru", "yoomoney.ru", "money.yandex.ru"
    };

    /** Магазины и соцсети: такие ссылки отдаём наружу — пусть открывает
     *  их собственное приложение, там у женщины уже есть вход и корзина. */
    private static final String[] OUTSIDE = {
            "wildberries.ru", "ozon.ru", "lamoda.ru", "market.yandex.ru",
            "aliexpress.ru", "letu.ru", "goldapple.ru", "rivegauche.ru",
            "t.me", "telegram.me", "instagram.com", "vk.com", "max.ru"
    };

    private WebView web;
    private View offline;
    private ValueCallback<Uri[]> filePick;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_main);

        web = findViewById(R.id.web);
        offline = findViewById(R.id.offline);
        Button retry = findViewById(R.id.retry);
        retry.setOnClickListener(v -> load());

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        /* Весь LOOKA держит данные в памяти браузера: анкета, гардероб, курсы,
           вход в аккаунт. Без этих двух строк приложение каждый раз забывало бы
           человека, и это была бы худшая версия сайта, а не лучшая. */
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return route(r.getUrl());
            }

            @Override
            public void onPageFinished(WebView v, String url) {
                if (online()) offline.setVisibility(View.GONE);
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            /* Выбор фото: примерка, разбор гардероба и фото в сообщениях.
               Без этого кнопки «добавить фото» в приложении молчали бы. */
            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb,
                                             FileChooserParams params) {
                if (filePick != null) filePick.onReceiveValue(null);
                filePick = cb;
                try {
                    startActivityForResult(params.createIntent(), 1001);
                    return true;
                } catch (ActivityNotFoundException e) {
                    filePick = null;
                    return false;
                }
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                request.deny();   /* камеру напрямую не отдаём: фото выбирается файлом */
            }
        });

        /* Кнопка «назад» ведёт себя как в браузере: возвращает на прошлый экран
           сайта, а не выкидывает из приложения с первого нажатия. */
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (web.canGoBack()) web.goBack();
                else finish();
            }
        });

        if (state != null) web.restoreState(state);
        else load();
    }

    private void load() {
        if (!online()) {
            offline.setVisibility(View.VISIBLE);
            TextView t = findViewById(R.id.offlineText);
            t.setText(R.string.offline_text);
            return;
        }
        offline.setVisibility(View.GONE);
        web.loadUrl(HOME);
    }

    private boolean online() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return true;
        NetworkCapabilities c = cm.getNetworkCapabilities(cm.getActiveNetwork());
        return c != null && c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    /** true — ссылку забираем себе и открываем снаружи; false — идём внутри. */
    private boolean route(Uri uri) {
        String url = uri.toString();
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();

        /* звонки, почта, ссылки на приложения — всегда наружу */
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return open(url);
        }
        for (String h : INSIDE) {
            if (host.equals(h) || host.endsWith("." + h)) return false;
        }
        for (String h : OUTSIDE) {
            if (host.equals(h) || host.endsWith("." + h)) return open(url);
        }
        /* незнакомый сайт — тоже наружу: внутри приложения место только LOOKA */
        return open(url);
    }

    private boolean open(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (ActivityNotFoundException e) {
            return false;   /* открыть нечем — пусть грузится внутри */
        }
        return true;
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != 1001) return;
        if (filePick == null) return;
        filePick.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(res, data));
        filePick = null;
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        web.saveState(out);
    }
}
