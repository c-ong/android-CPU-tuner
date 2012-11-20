package ch.amana.android.cputuner.view.activity;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Toast;
import ch.amana.android.cputuner.R;
import ch.amana.android.cputuner.helper.GeneralMenuHelper;
import ch.amana.android.cputuner.helper.GuiUtils;
import ch.amana.android.cputuner.helper.InstallHelper;
import ch.amana.android.cputuner.helper.SettingsStorage;
import ch.amana.android.cputuner.log.Logger;
import ch.amana.android.cputuner.log.Notifier;
import ch.amana.android.cputuner.service.TunerService;
import ch.amana.android.cputuner.view.adapter.PagerAdapter;
import ch.amana.android.cputuner.view.adapter.PagerAdapter.PagerItem;
import ch.amana.android.cputuner.view.widget.ActionBarWrapper;
import ch.amana.android.cputuner.view.widget.CputunerActionBar;

import com.markupartist.android.widget.ActionBar;

public class CpuTunerViewpagerActivity extends FragmentActivity {

	private boolean doCheckConfig = true;
	private PagerAdapter pagerAdapter;

	private static final int[] lock = new int[1];
	private CpuTunerReceiver receiver;
	private final Set<StateChangeListener> stateChangeListeners = new HashSet<StateChangeListener>();
	private ActionBarWrapper actionBarWrapper;

	public interface StateChangeListener {

		void profileChanged();

		void deviceStatusChanged();

		void triggerChanged();

	}

	protected class CpuTunerReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			deviceStatusChanged();
			if (Notifier.BROADCAST_TRIGGER_CHANGED.equals(action)) {
				triggerChanged();
			}
			if (Notifier.BROADCAST_PROFILE_CHANGED.equals(action)) {
				profileChanged();
			}

		}
	}

	public void registerReceiver() {
		synchronized (lock) {
			IntentFilter deviceStatusFilter = new IntentFilter(Notifier.BROADCAST_DEVICESTATUS_CHANGED);
			IntentFilter triggerFilter = new IntentFilter(Notifier.BROADCAST_TRIGGER_CHANGED);
			IntentFilter profileFilter = new IntentFilter(Notifier.BROADCAST_PROFILE_CHANGED);
			receiver = new CpuTunerReceiver();
			registerReceiver(receiver, deviceStatusFilter);
			registerReceiver(receiver, triggerFilter);
			registerReceiver(receiver, profileFilter);
			Logger.i("Registered CpuTunerReceiver");
		}
	}

	public void unregisterReceiver() {
		synchronized (lock) {
			if (receiver != null) {
				try {
					unregisterReceiver(receiver);
					receiver = null;
				} catch (Throwable e) {
					Logger.w("Could not unregister BatteryReceiver", e);
				}
			}
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		SettingsStorage settings = SettingsStorage.getInstance();
		if (!settings.hasHoloTheme()) {
			requestWindowFeature(Window.FEATURE_NO_TITLE);
		}


		String lang = settings.getLanguage();
		if (!"".equals(lang)) {
			GuiUtils.setLanguage(this, lang);
		}
		GuiUtils.setLanguage(this);

		if (!sanityChecks(settings)) {
			return;
		}

		setContentView(R.layout.viewpager);

		CputunerActionBar cputunerActionBar = (CputunerActionBar) findViewById(R.id.abCpuTuner);
		if (settings.hasHoloTheme()) {
			android.app.ActionBar bar = getActionBar();
			actionBarWrapper = new ActionBarWrapper(this, bar);
			setTitle(R.string.app_name);
			if (Logger.DEBUG) {
				bar.setSubtitle("DEBUG MODE" + " (" + settings.getVersionName() + ")");
			}
			cputunerActionBar.setVisibility(View.GONE);
		} else {

			String title = getString(R.string.app_name);
			if (Logger.DEBUG) {
				title = title + " - DEBUG MODE" + " (" + settings.getVersionName() + ")";
			}
			cputunerActionBar.setTitle(title);
			cputunerActionBar.setHomeLogo(R.drawable.icon);
			cputunerActionBar.setHomeTitleAction(new ActionBar.IntentAction(this, CpuTunerViewpagerActivity.getStartIntent(this), R.drawable.icon));
			actionBarWrapper = new ActionBarWrapper(cputunerActionBar);
		}

		ViewPager pager = (ViewPager) findViewById(R.id.pager);
		pagerAdapter = new PagerAdapter(this, getSupportFragmentManager());
		pager.setAdapter(pagerAdapter);

		pager.setOnPageChangeListener(new OnPageChangeListener() {

			@Override
			public void onPageSelected(int pos) {
				PagerItem pagerItem = (PagerItem) pagerAdapter.getItem(pos);
				pagerItem.pageIsActive(CpuTunerViewpagerActivity.this);
			}

			@Override
			public void onPageScrolled(int arg0, float arg1, int arg2) {
			}

			@Override
			public void onPageScrollStateChanged(int arg0) {
			}
		});

	}

	private boolean sanityChecks(SettingsStorage settings) {
		if (settings.isFirstRun() && !InstallHelper.hasConfig(this)) {
			startActivity(new Intent(getApplicationContext(), FirstRunActivity.class));
			finish();
			return false;
		}
		if (doCheckConfig && !InstallHelper.hasConfig(this)) {
			AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
			alertBuilder.setTitle(R.string.title_no_configuration);
			alertBuilder.setMessage(R.string.label_no_configuration);
			alertBuilder.setPositiveButton(R.string.load, new OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					InstallHelper.ensureConfiguration(CpuTunerViewpagerActivity.this, false);
				}
			});
			alertBuilder.setNegativeButton(R.string.cont, new OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					doCheckConfig = false;
				}
			});
			AlertDialog alert = alertBuilder.create();
			alert.show();
		}
		if (!SettingsStorage.getInstance().isUserLevelSet()) {
			UserExperianceLevelChooser uec = new UserExperianceLevelChooser(this, false);
			uec.show();
		}
		return true;
	}

	public static Intent getStartIntent(Context ctx) {
		Intent intent = new Intent(ctx, CpuTunerViewpagerActivity.class);
		//		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_WHEN_TASK_RESET | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		return intent;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		super.onCreateOptionsMenu(menu);
		//		return pagerAdapter.onPrepareOptionsMenu(menu);
		//		menu.clear();
		MenuInflater menuInflater = getMenuInflater();
		//		menuInflater.inflate(R.menu.gerneral_help_menu, menu);
		menuInflater.inflate(R.menu.gerneral_options_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (GeneralMenuHelper.onOptionsItemSelected(this, item, null)) {
			return true;
		}
		return false;
	}

	@Override
	protected void onResume() {
		super.onResume();
		registerReceiver();
		if (TunerService.hasWakelock()) {
			Toast.makeText(this, "Still holding a wakelock!", Toast.LENGTH_LONG).show();
		}
	}

	@Override
	protected void onPause() {
		unregisterReceiver();
		super.onPause();
	}

	public void addStateChangeListener(StateChangeListener listener) {
		stateChangeListeners.add(listener);
	}

	public void removeStateChangeListener(StateChangeListener listener) {
		stateChangeListeners.remove(listener);
	}

	private void profileChanged() {
		for (Iterator<StateChangeListener> iterator = stateChangeListeners.iterator(); iterator.hasNext();) {
			try {
				iterator.next().profileChanged();
			} catch (Exception e) {
			}
		}
	}

	private void triggerChanged() {
		for (Iterator<StateChangeListener> iterator = stateChangeListeners.iterator(); iterator.hasNext();) {
			try {
				iterator.next().triggerChanged();
			} catch (Exception e) {
			}
		}
	}

	private void deviceStatusChanged() {
		for (Iterator<StateChangeListener> iterator = stateChangeListeners.iterator(); iterator.hasNext();) {
			try {
				iterator.next().deviceStatusChanged();
			} catch (Exception e) {
			}
		}
	}

	public ActionBarWrapper getActionBarWrapper() {
		return actionBarWrapper;
	}

}
