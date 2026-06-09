package com.example.hackofiesta

import android.graphics.Color
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.hackofiesta.Database.OverallDatabase
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.BubbleChart
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.charts.HorizontalBarChart
import com.github.mikephil.charting.charts.LineChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.charts.RadarChart
import com.github.mikephil.charting.components.Legend
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.data.BubbleData
import com.github.mikephil.charting.data.BubbleDataSet
import com.github.mikephil.charting.data.BubbleEntry
import com.github.mikephil.charting.data.Entry
import com.github.mikephil.charting.data.LineData
import com.github.mikephil.charting.data.LineDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.data.RadarData
import com.github.mikephil.charting.data.RadarDataSet
import com.github.mikephil.charting.data.RadarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class StatisticsActivity : AppCompatActivity() {

    lateinit var database : OverallDatabase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_statistics)

        database = OverallDatabase.getDatabase(this);

        val barChart = findViewById<BarChart>(R.id.barChart)
//        val pieChart = findViewById<PieChart>(R.id.pieChart)

//        val lineChart = findViewById<LineChart>(R.id.lineChart)

//        val horizontalBarChart = findViewById<HorizontalBarChart>(R.id.horizontalBarChart)
//        val bubbleChart = findViewById<BubbleChart>(R.id.bubbleChart)
//        val combinedChart = findViewById<CombinedChart>(R.id.combinedChart);
//        val radarChart = findViewById<RadarChart>(R.id.radarChart);
        loadDataIntoChart(barChart)

//        loadDataIntoPieChart(pieChart);
//        loadDataLineChart(lineChart)
//        loadDataHorizontalBarChart(horizontalBarChart);
//        loadDataRadarChart(radarChart);
//        loadDataBubbleChart(bubbleChart);
//        loadDataCombinedChart(combinedChart);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun loadDataIntoChart(barChart: BarChart){

        val sharedPref = getSharedPreferences("vehicleState", MODE_PRIVATE);

        val selectedState = sharedPref.getString("selected_state",null);

        database.vehicleLocationDao().getVehicleDataByState(selectedState!!).observe(this@StatisticsActivity,{
            val entries = ArrayList<BarEntry>();
            val labels = ArrayList<String>();
            var i = 0;
            while (i<it.size){
                entries.add(BarEntry(i.toFloat(),it[i].vehicleCount!!.toFloat()));
                labels.add(it[i].currentCity ?: "Unknown");
                i++;
            }

            val barDataSet = BarDataSet(entries, "Vehicle Counts");
            barDataSet.colors = ColorTemplate.MATERIAL_COLORS.toList();

            barDataSet.valueTextColor = Color.DKGRAY;
            barDataSet.valueTextSize = 12f;
            barDataSet.highLightAlpha = 40

            val barData = BarData(barDataSet);

            barData.barWidth = 0.7f

            barChart.data = barData;
            barChart.setDrawGridBackground(false)
            barChart.setDrawBarShadow(false)
            barChart.setDrawValueAboveBar(true)
            barChart.description.isEnabled = false;
            barChart.animateY(1200);
            barChart.setExtraOffsets(10f,30f,10f,45f);
            barChart.setScaleEnabled(false);
            barChart.setPinchZoom(false);
            barChart.isDoubleTapToZoomEnabled = false;

            val xAxis = barChart.xAxis
            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
            xAxis.position = XAxis.XAxisPosition.BOTTOM;
            xAxis.setDrawGridLines(false);
            xAxis.setDrawAxisLine(true)
            xAxis.granularity = 1f
            xAxis.isGranularityEnabled = true;
            xAxis.labelRotationAngle = -45f
            xAxis.textColor = Color.parseColor("#455A64")
            xAxis.textSize = 11f
            xAxis.yOffset = 10f

            barChart.axisLeft.axisMinimum = 0f;
            barChart.axisLeft.gridColor = Color.parseColor("#EEEEEE")
            barChart.axisLeft.setDrawAxisLine(false)
            barChart.axisRight.isEnabled = false;
            barChart.axisLeft.spaceTop = 20f
            barChart.axisLeft.textColor = Color.parseColor("#90A4AE")

            val legend = barChart.legend;
            legend.isEnabled = true
            legend.form = Legend.LegendForm.CIRCLE;
            legend.horizontalAlignment = Legend.LegendHorizontalAlignment.CENTER
            legend.verticalAlignment = Legend.LegendVerticalAlignment.TOP
            legend.horizontalAlignment = Legend.LegendHorizontalAlignment.RIGHT
            legend.orientation = Legend.LegendOrientation.HORIZONTAL
            legend.setDrawInside(false)

            barChart.invalidate();

        })
    }
//    private fun loadDataIntoPieChart(pieChart: PieChart){
//        database.vehicleLocationDao().getVehicleLocationAll().observe(this@StatisticsActivity,{
//            val entries = ArrayList<PieEntry>();
//            var i = 0;
//            while (i<it.size){
//                entries.add(PieEntry(it[i].vehicleCount!!.toFloat(),it[i].currentCity ?: "Unknown"));
//                i++;
//            }
//
//            val pieDataSet = com.github.mikephil.charting.data.PieDataSet(entries, "City Distribution");
//            pieDataSet.colors = com.github.mikephil.charting.utils.ColorTemplate.COLORFUL_COLORS.toList();
//
//            pieDataSet.valueTextColor = android.graphics.Color.WHITE;
//            pieDataSet.valueTextSize = 12f;
//
//            val pieData = com.github.mikephil.charting.data.PieData(pieDataSet);
//
//            pieChart.data = pieData;
//            pieChart.centerText = "Vehicles"
//
//            pieChart.description.isEnabled = false;
//            pieChart.animateXY(1000,1000);
//
//            pieChart.invalidate();
//
//        })
//    }

//    private fun loadDataLineChart(lineChart: LineChart){
//        database.vehicleLocationDao().getVehicleLocationAll().observe(this@StatisticsActivity,{
//            val entries = ArrayList<Entry>();
//            val labels = ArrayList<String>();
//            var i = 0;
//            while (i<it.size){
//                entries.add(BarEntry(i.toFloat(),it[i].vehicleCount!!.toFloat()));
//                labels.add(it[i].currentCity ?: "Unknown");
//                i++;
//            }
//
//            val lineDataSet = com.github.mikephil.charting.data.LineDataSet(entries, "Vehicle Counts");
//            lineDataSet.color = ColorTemplate.JOYFUL_COLORS[0];
//            lineDataSet.setCircleColor(ColorTemplate.JOYFUL_COLORS[0]);
////            lineDataSet.valueTextColor = android.graphics.Color.BLACK;
//            lineDataSet.valueTextSize = 10f;
//            lineDataSet.lineWidth = 2f;
//
//            val lineData = LineData(lineDataSet);
//
//            lineChart.data = lineData;
//
//            lineChart.description.isEnabled = false;
//            lineChart.animateX(1000);
//
//            val xAxis = lineChart.xAxis
//            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
//            xAxis.position = XAxis.XAxisPosition.BOTTOM;
//            xAxis.setDrawGridLines(false);
//            xAxis.granularity = 1f
//            xAxis.isGranularityEnabled = true;
//
//            lineChart.axisLeft.axisMinimum = 0f;
//            lineChart.axisRight.isEnabled = false;
//
//            lineChart.invalidate();
//
//        })
//    }

//    private fun loadDataHorizontalBarChart(horizontalBarChart: HorizontalBarChart){
//        database.vehicleLocationDao().getVehicleLocationAll().observe(this@StatisticsActivity,{
//            val entries = ArrayList<BarEntry>();
//            val labels = ArrayList<String>();
//            var i = 0;
//            while (i<it.size){
//                entries.add(BarEntry(i.toFloat(),it[i].vehicleCount!!.toFloat()));
//                labels.add(it[i].currentCity ?: "Unknown");
//                i++;
//            }
//
//            val barDataSet = BarDataSet(entries, "Vehicle Counts");
//            barDataSet.colors = ColorTemplate.LIBERTY_COLORS.toList();
//
//            barDataSet.valueTextColor = android.graphics.Color.BLACK;
//            barDataSet.valueTextSize = 12f;
//
//            val barData = com.github.mikephil.charting.data.BarData(barDataSet);
//
//            horizontalBarChart.data = barData;
//
//            horizontalBarChart.description.isEnabled = false;
//            horizontalBarChart.animateY(1000);
//
//            val xAxis = horizontalBarChart.xAxis
//            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
//            xAxis.position = XAxis.XAxisPosition.BOTTOM;
//            xAxis.setDrawGridLines(false);
//            xAxis.granularity = 1f
//            xAxis.isGranularityEnabled = true;
//
//            horizontalBarChart.axisLeft.axisMinimum = 0f;
//            horizontalBarChart.axisRight.isEnabled = false;
//
//            horizontalBarChart.setExtraOffsets(40f,0f,0f,0f)
//            xAxis.setLabelCount(it.size);
//
//            horizontalBarChart.invalidate();
//
//        })
//    }

//    private fun loadDataRadarChart(radarChart: RadarChart){
//        database.vehicleLocationDao().getVehicleLocationAll().observe(this@StatisticsActivity,{
//            val entries = ArrayList< RadarEntry>();
//            val labels = ArrayList<String>();
//            var i = 0;
//            while (i<it.size){
//                entries.add(RadarEntry(it[i].vehicleCount!!.toFloat()));
//                labels.add(it[i].currentCity ?: "Unknown");
//                i++;
//            }
//
//            val radarDataSet = RadarDataSet(entries, "Vehicle Counts");
//            radarDataSet.color = Color.RED;
//            radarDataSet.fillColor = Color.RED;
//            radarDataSet.setDrawFilled(true);
//            radarDataSet.fillAlpha = 180;
//
//            radarDataSet.valueTextColor = Color.BLACK;
//            radarDataSet.valueTextSize = 12f;
//
//            val radarData = RadarData(radarDataSet);
//
//            radarChart.data = radarData;
//
//            radarChart.description.isEnabled = false;
//            radarChart.animateXY(1000,1000);
//
//            val xAxis = radarChart.xAxis
//            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
////            xAxis.position = XAxis.XAxisPosition.BOTTOM;
////            xAxis.setDrawGridLines(false);
//            xAxis.setLabelCount(labels.size,true);
//            xAxis.granularity = 1f
//            xAxis.isGranularityEnabled = true;
//            xAxis.textSize = 10f;
//
////            radarChart.axisLeft.axisMinimum = 0f;
////            radarChart.axisRight.isEnabled = false;
//            val yAxis = radarChart.yAxis;
//            yAxis.axisMinimum = 0f;
//            yAxis.setDrawLabels(false);
//            radarChart.setExtraOffsets(30f,0f,40f,0f);
//
//            radarChart.invalidate();
//
//        })
//    }

//    private fun loadDataBubbleChart(bubbleChart: BubbleChart){
//        database.vehicleLocationDao().getVehicleLocationAll().observe(this@StatisticsActivity,{
//            val entries = ArrayList<BubbleEntry>();
//            val labels = ArrayList<String>();
//            var i = 0;
//            while (i<it.size){
//                entries.add(BubbleEntry(i.toFloat(),it[i].vehicleCount!!.toFloat(),it[i].vehicleCount!!.toFloat()));
//                labels.add(it[i].currentCity ?: "Unknown");
//                i++;
//            }
//
//            val bubbleDataSet = BubbleDataSet(entries, "Vehicle Counts");
//            bubbleDataSet.colors = ColorTemplate.JOYFUL_COLORS.toList();
//
////            bubbleDataSet.valueTextColor = android.graphics.Color.BLACK;
//            bubbleDataSet.valueTextSize = 11f;
//
//            val bubbleData = BubbleData(bubbleDataSet);
//
//            bubbleChart.data = bubbleData;
//
//            bubbleChart.description.isEnabled = false;
//            bubbleChart.animateXY(1000,1000);
//
//            val xAxis = bubbleChart.xAxis
//            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
//            xAxis.position = XAxis.XAxisPosition.BOTTOM;
//            xAxis.setDrawGridLines(false);
//            xAxis.granularity = 1f
//            xAxis.isGranularityEnabled = true;
//            xAxis.axisMinimum = -0.5f
//            xAxis.axisMaximum = it.size.toFloat() - 0.5f
//
//            bubbleChart.axisLeft.axisMinimum = 0f;
//            bubbleChart.axisRight.isEnabled = false;
//
//            bubbleChart.invalidate();
//
//        })
//    }

//    private fun loadDataCombinedChart(combinedChart: CombinedChart){
//        database.vehicleLocationDao().getVehicleLocationAll().observe(this@StatisticsActivity,{
//            val barEntries = ArrayList<BarEntry>();
//            val lineEntries = ArrayList<Entry>();
//            val labels = ArrayList<String>();
//
//            var i = 0;
//            while (i<it.size){
//                barEntries.add(BarEntry(i.toFloat(),it[i].vehicleCount!!.toFloat()));
//                lineEntries.add(Entry(i.toFloat(),it[i].vehicleCount!!.toFloat()));
//                labels.add(it[i].currentCity ?: "Unknown");
//                i++;
//            }
//
//            val barDataSet = BarDataSet(barEntries, "Vehicle Counts Bar");
//            barDataSet.colors = ColorTemplate.LIBERTY_COLORS.toList();
//
//            barDataSet.setDrawValues(false);
//
////            barDataSet.color = Color.parseColor("#80CBC4");
//            barDataSet.valueTextColor = Color.BLACK;
//            barDataSet.valueTextSize = 12f;
//
//            val lineDataSet = LineDataSet(lineEntries, "Vehicle Counts Line");
////            barDataSet.colors = ColorTemplate.MATERIAL_COLORS.toList();
//
//            lineDataSet.color = Color.RED;
//            lineDataSet.setCircleColor(Color.RED);
//            lineDataSet.lineWidth = 2.5f;
//            lineDataSet.circleRadius = 4f
//            lineDataSet.mode = LineDataSet.Mode.CUBIC_BEZIER;
//            lineDataSet.valueTextColor = Color.BLACK;
//            lineDataSet.valueTextSize = 12f;
//
//            lineDataSet.setDrawValues(true);
//
//            val barData = BarData(barDataSet);
//
//            val lineData = LineData(lineDataSet);
//
//            val combinedData = com.github.mikephil.charting.data.CombinedData();
//            combinedData.setData(barData);
//            combinedData.setData(lineData);
//
//            combinedChart.data = combinedData;
//
//            combinedChart.description.isEnabled = false;
//            combinedChart.animateY(1000);
//
//            val xAxis = combinedChart.xAxis
//            xAxis.valueFormatter = IndexAxisValueFormatter(labels)
//            xAxis.position = XAxis.XAxisPosition.BOTTOM;
//            xAxis.setDrawGridLines(false);
//            xAxis.granularity = 1f
//            xAxis.isGranularityEnabled = true;
//
//            combinedChart.axisLeft.axisMinimum = 0f;
//            combinedChart.axisRight.isEnabled = false;
//
//            combinedChart.invalidate();
//
//        })
//    }
}