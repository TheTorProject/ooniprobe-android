package org.openobservatory.ooniprobe.activity;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.WebView;
import android.widget.TextView;
import android.widget.Toast;

import org.apache.commons.io.FileUtils;
import org.openobservatory.ooniprobe.R;
import org.openobservatory.ooniprobe.client.callback.GetMeasurementJsonCallback;
import org.openobservatory.ooniprobe.client.callback.GetMeasurementsCallback;
import org.openobservatory.ooniprobe.common.ReachabilityManager;
import org.openobservatory.ooniprobe.model.api.ApiMeasurement;
import org.openobservatory.ooniprobe.model.database.Measurement;

import androidx.annotation.Nullable;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;

import java.io.File;
import java.nio.charset.Charset;

import butterknife.BindView;
import butterknife.ButterKnife;
import localhost.toolkit.app.fragment.MessageDialogFragment;
import okhttp3.Request;

public class TextActivity extends AbstractActivity {
	private Measurement measurement;
	private static final int TYPE_LOG = 1;
	private static final int TYPE_JSON = 2;
	private static final String TEST = "test";
	private static final String TYPE = "type";
	@BindView(R.id.webView) WebView webView;

	public static Intent newIntent(Context context, int type, Measurement measurement) {
		return new Intent(context, TextActivity.class).putExtra(TYPE, type).putExtra(TEST, measurement);
	}

	@Override protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.text);
		ButterKnife.bind(this);
		measurement = (Measurement) getIntent().getSerializableExtra(TEST);
		new Thread(() -> runOnUiThread(() -> showText())).start();
	}

	@Override public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.clipboard, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.clipboard:
				//((ClipboardManager) getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText(getString(R.string.General_AppName), textView.getText().toString()));
				Toast.makeText(this, R.string.Toast_CopiedToClipboard, Toast.LENGTH_SHORT).show();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	public void showText(){
		switch (getIntent().getIntExtra(TYPE, 0)) {
			case TYPE_LOG:
				//Try to open file, if it doesn't exist dont show Error dialog immediately but try to download the json from internet
				try {
					File entryFile = Measurement.getEntryFile(this, measurement.id, measurement.test_name);
					String json = FileUtils.readFileToString(entryFile, Charset.forName("UTF-8"));
					json = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(new JsonParser().parse(json));
					webView.loadData(json, "text/html; charset=utf-8", "utf-8");
				} catch (Exception e) {
					e.printStackTrace();
					if (ReachabilityManager.getNetworkType(this).equals(ReachabilityManager.NO_INTERNET)) {
						new MessageDialogFragment.Builder()
								.withTitle(getString(R.string.Modal_Error))
								.withMessage(getString(R.string.Modal_Error_RawDataNoInternet))
								.build().show(getSupportFragmentManager(), null);
					}
					getApiClient().getMeasurement(measurement.report_id, null).enqueue(new GetMeasurementsCallback() {
						@Override
						public void onSuccess(ApiMeasurement.Result result) {
							//TODO load measurement_url into WebView from the internet
							getOkHttpClient().newCall(new Request.Builder().url(result.measurement_url).build()).enqueue(new GetMeasurementJsonCallback() {
								@Override
								public void onSuccess(String json) {
									webView.loadData(json, "text/html; charset=utf-8", "utf-8");
								}
								@Override
								public void onError(String msg) {
									new MessageDialogFragment.Builder()
											.withTitle(getString(R.string.Modal_Error))
											.withMessage(msg)
											.build().show(getSupportFragmentManager(), null);
								}
							});
						}
						@Override
						public void onError(String msg) {
							new MessageDialogFragment.Builder()
									.withTitle(getString(R.string.Modal_Error))
									.withMessage(msg)
									.build().show(getSupportFragmentManager(), null);
						}
					});
				}
			case TYPE_JSON:
				try {
					File logFile = Measurement.getLogFile(this, measurement.result.id, measurement.test_name);
					String log = FileUtils.readFileToString(logFile, Charset.forName("UTF-8"));
					webView.loadData(log, "text/html; charset=utf-8", "utf-8");
				} catch (Exception e) {
					e.printStackTrace();
					new MessageDialogFragment.Builder()
							.withTitle(getString(R.string.Modal_Error_LogNotFound))
							.build().show(getSupportFragmentManager(), null);
				}
		}
	}
}
