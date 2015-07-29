package nodomain.freeyourgadget.gadgetbridge.activities;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v4.content.LocalBroadcastManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.github.mikephil.charting.charts.BarLineChartBase;
import com.github.mikephil.charting.charts.Chart;
import com.github.mikephil.charting.charts.PieChart;
import com.github.mikephil.charting.components.LimitLine;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.PieData;
import com.github.mikephil.charting.data.PieDataSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import nodomain.freeyourgadget.gadgetbridge.ControlCenter;
import nodomain.freeyourgadget.gadgetbridge.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.charts.ActivityAnalysis;
import nodomain.freeyourgadget.gadgetbridge.model.ActivitySample;


public class WeekStepsChartFragment extends AbstractChartFragment {
    protected static final Logger LOG = LoggerFactory.getLogger(WeekStepsChartFragment.class);

    private Locale mLocale;
    private int mTargetSteps;

    private BarLineChartBase mWeekStepsChart;
    private PieChart mTodayStepsChart;

    private GBDevice mGBDevice = null;

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(ACTION_REFRESH)) {
                refresh();
            }
        }
    };

    private void refresh() {
        Calendar day = Calendar.getInstance();

        //NB: we could have omitted the day, but this way we can move things to the past easily
        refreshDaySteps(mTodayStepsChart, day);
        refreshWeekBeforeSteps(mWeekStepsChart, day);

        mWeekStepsChart.invalidate();
        mTodayStepsChart.invalidate();
    }


    private void refreshWeekBeforeSteps(BarLineChartBase barChart, Calendar day) {

        ActivityAnalysis analysis = new ActivityAnalysis();

        day.add(Calendar.DATE, -7);
        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        for (int counter = 0; counter < 7; counter++) {
            entries.add(new BarEntry(analysis.calculateTotalSteps(getSamplesOfDay(day)), counter));
            labels.add(day.getDisplayName(Calendar.DAY_OF_WEEK, Calendar.SHORT, mLocale));
            day.add(Calendar.DATE, 1);
        }

        BarDataSet set = new BarDataSet(entries, "");
        set.setColor(akActivity.color);

        BarData data = new BarData(labels, set);

        LimitLine target = new LimitLine(mTargetSteps);

        barChart.getAxisLeft().addLimitLine(target);

        setupLegend(barChart);
        barChart.setData(data);
        barChart.getLegend().setEnabled(false);
    }

    private void refreshDaySteps(PieChart pieChart, Calendar day) {
        ActivityAnalysis analysis = new ActivityAnalysis();

        int totalSteps = analysis.calculateTotalSteps(getSamplesOfDay(day));

        pieChart.setCenterText(NumberFormat.getNumberInstance(mLocale).format(totalSteps));
        PieData data = new PieData();
        List<Entry> entries = new ArrayList<>();
        List<Integer> colors = new ArrayList<>();

        entries.add(new Entry(totalSteps, 0));
        colors.add(akActivity.color);
        data.addXValue("");

        entries.add(new Entry((mTargetSteps - totalSteps), 1));
        colors.add(Color.GRAY);
        data.addXValue("");

        PieDataSet set = new PieDataSet(entries, "");
        set.setColors(colors);
        data.setDataSet(set);
        data.setDrawValues(false);
        pieChart.setData(data);

        pieChart.getLegend().setEnabled(false);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        mLocale = getResources().getConfiguration().locale;

        //TODO: through mGBDevice we should be able to retrieve the steps goal set by the user
        mTargetSteps = 10000;


        View rootView = inflater.inflate(R.layout.fragment_sleepchart, container, false);

        Bundle extras = getActivity().getIntent().getExtras();
        if (extras != null) {
            mGBDevice = extras.getParcelable(GBDevice.EXTRA_DEVICE);
        }

        IntentFilter filter = new IntentFilter();
        filter.addAction(ControlCenter.ACTION_QUIT);
        filter.addAction(ACTION_REFRESH);

        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(mReceiver, filter);

        mWeekStepsChart = (BarLineChartBase) rootView.findViewById(R.id.sleepchart);
        mTodayStepsChart = (PieChart) rootView.findViewById(R.id.sleepchart_pie_light_deep);

        setupWeekStepsChart();
        setupTodayStepsChart();

        refresh();

        return rootView;
    }

    private void setupTodayStepsChart() {
        mTodayStepsChart.setBackgroundColor(BACKGROUND_COLOR);
        mTodayStepsChart.setDescriptionColor(DESCRIPTION_COLOR);
        mTodayStepsChart.setDescription("Steps today, target: " + mTargetSteps);
        mTodayStepsChart.setNoDataTextDescription("");
        mTodayStepsChart.setNoDataText("");
    }

    @Override
    public void onDestroy() {
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mReceiver);
        super.onDestroy();
    }


    private void setupWeekStepsChart() {
        mWeekStepsChart.setBackgroundColor(BACKGROUND_COLOR);
        mWeekStepsChart.setDescriptionColor(DESCRIPTION_COLOR);
        mWeekStepsChart.setDescription("");

        configureBarLineChartDefaults(mWeekStepsChart);

        XAxis x = mWeekStepsChart.getXAxis();
        x.setDrawLabels(true);
        x.setDrawGridLines(false);
        x.setEnabled(true);
        x.setTextColor(CHART_TEXT_COLOR);
        x.setDrawLimitLinesBehindData(true);

        YAxis y = mWeekStepsChart.getAxisLeft();
        y.setDrawGridLines(false);
        y.setDrawTopYLabelEntry(false);
        y.setTextColor(CHART_TEXT_COLOR);

        y.setEnabled(true);

        YAxis yAxisRight = mWeekStepsChart.getAxisRight();
        yAxisRight.setDrawGridLines(false);
        yAxisRight.setEnabled(false);
        yAxisRight.setDrawLabels(false);
        yAxisRight.setDrawTopYLabelEntry(false);
        yAxisRight.setTextColor(CHART_TEXT_COLOR);

    }

    protected void setupLegend(Chart chart) {
        List<Integer> legendColors = new ArrayList<>(1);
        List<String> legendLabels = new ArrayList<>(1);
        legendColors.add(akActivity.color);
        legendLabels.add("Steps");
        chart.getLegend().setColors(legendColors);
        chart.getLegend().setLabels(legendLabels);
        chart.getLegend().setTextColor(LEGEND_TEXT_COLOR);
    }

    private List<ActivitySample> getSamplesOfDay(Calendar day) {
        int startTs;
        int endTs;

        day.set(Calendar.HOUR_OF_DAY, 0);
        day.set(Calendar.MINUTE, 0);
        day.set(Calendar.SECOND, 0);
        startTs = (int) (day.getTimeInMillis() / 1000);

        day.set(Calendar.HOUR_OF_DAY, 23);
        day.set(Calendar.MINUTE, 59);
        day.set(Calendar.SECOND, 59);
        endTs = (int) (day.getTimeInMillis() / 1000);

        return getSamples(mGBDevice, startTs, endTs);
    }

    @Override
    protected List<ActivitySample> getSamples(GBDevice device, int tsFrom, int tsTo) {
        return super.getAllSamples(device, tsFrom, tsTo);
    }
}
