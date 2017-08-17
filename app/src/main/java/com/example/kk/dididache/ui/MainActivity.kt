package com.example.kk.dididache.ui

import android.Manifest
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import com.baidu.location.*
import com.baidu.mapapi.map.*
import com.baidu.mapapi.map.BaiduMap.OnMapStatusChangeListener
import com.baidu.mapapi.model.LatLng
import com.baidu.mapapi.search.core.SearchResult
import com.baidu.mapapi.search.poi.*
import com.baidu.mapapi.search.sug.OnGetSuggestionResultListener
import com.baidu.mapapi.search.sug.SuggestionResult
import com.baidu.mapapi.search.sug.SuggestionSearch
import com.baidu.mapapi.search.sug.SuggestionSearchOption
import com.example.kk.dididache.*
import com.example.kk.dididache.model.*
import com.orhanobut.logger.Logger
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.activity_main.view.*
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.jetbrains.anko.imageResource
import org.jetbrains.anko.sdk25.coroutines.onClick
import org.jetbrains.anko.sdk25.coroutines.onTouch
import java.util.*

class MainActivity : BaseActivity() {
    //定位相关
    private val locClient by lazy { LocationClient(this) }
    private val locationListener by lazy { MyLocationListener() } //定位监听
    private var curPoint: LatLng = LatLng(0.0, 0.0)//当前经纬度
        get() = LatLng(locData?.latitude ?: 0.0, locData?.longitude ?: 0.0)
    private var locData: MyLocationData? = null//坐标信息

    //UI相关
    private val map by lazy { mapView.map }
    private var heatMap: HeatMap? = null
        set(value) {
            field?.removeHeatMap()
            field = value
            map.addHeatMap(field)//自动添加
        }
    private var chartDialog: ChartDialog? = null
    private var isUnusualShowing = false//是否在显示异常点
    private var suggestAdapter: SearchItemAdapter? = null
    private val poimarkerIcon = BitmapDescriptorFactory.fromResource(R.drawable.destination_point_blue)
    private var time: Calendar = Calendar.getInstance()

    //搜索相关
    private var suggestSearch = SuggestionSearch.newInstance()
    private var poiSearch = PoiSearch.newInstance()

    //地图状态变化监听器
    private var mapStateChangeListener: OnMapStatusChangeListener = object : OnMapStatusChangeListener {
        override fun onMapStatusChangeStart(p0: MapStatus?) {

        }

        override fun onMapStatusChange(p0: MapStatus?) {

        }

        override fun onMapStatusChangeFinish(p0: MapStatus?) {
            Logger.i("地图边界${p0?.bound}")
            //Http.getInstance().getCarsUnderBounds(map.mapStatus.bound)
        }
    }
    private var onMapClickListener: BaiduMap.OnMapClickListener = object : BaiduMap.OnMapClickListener {
        override fun onMapClick(p0: LatLng?) {
            time.set(2017, 2, 28, 4, 5, 5)
            chartDialog?.show(time)
        }

        override fun onMapPoiClick(p0: MapPoi?): Boolean {
            return true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        EventBus.getDefault().register(this)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        requsetPermission()//请求权限


    }

    private fun requsetPermission() {
        val permissionList = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            permissionList.add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED)
            permissionList.add(Manifest.permission.READ_PHONE_STATE)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)
            permissionList.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (!permissionList.isEmpty())
            ActivityCompat.requestPermissions(this, permissionList.toTypedArray(), 1)
        else {

            initMap()//初始化地图
            initLocation()
            initLoc()
        }//同意权限开启定位
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            1 -> {
                if (grantResults.isNotEmpty()) {
                    (0 until grantResults.size)
                            .filter { it != PackageManager.PERMISSION_GRANTED }
                            .forEach {
                                showToast("有部分权限未授权")
                                finish()
                                return
                            }
                    initMap()//初始化地图
                    initLocation()
                    initLoc()//授权完毕，初始化定位
                } else {
                    showToast("发生未知错误")
                    return
                }
            }
        }
    }

    //定位监听器,还可以对热点监听等等
    inner class MyLocationListener : BDAbstractLocationListener() {

        override fun onReceiveLocation(p0: BDLocation?) {
            if (p0 == null) return
            //复制坐标
            curPoint = LatLng(p0.latitude, p0.longitude)
            locData = MyLocationData.Builder()
                    .accuracy(p0.radius)
                    .latitude(p0.latitude)
                    .longitude(p0.longitude)
                    .build()
            map.setMyLocationData(locData)

            /***********/

            if (HeatInfo.lastTime == null) {
                val lastTime = Calendar.getInstance()
                lastTime.set(2017, 2, 28, 0, 0, 1)
                HeatInfo.lastTime = lastTime.toStr()
            }
            val newTime = HeatInfo.lastTime!!.toCalender()
            newTime.add(Calendar.SECOND, 30)
            val info: HeatInfo = HeatInfo(HeatInfo.lastTime!!, newTime.toStr(), map.mapStatus.bound.northeast, map.mapStatus.bound.southwest)
            Http.getInstance().cancelCall(Http.TAG_HEATPOINTS)
            Http.getInstance().getHeatPoints(info)
            HeatInfo.lastTime = newTime.toStr()

            /***********/

        }
    }

    //默认定位在广州市中心
    private fun initLocation() {
        map.animateMapStatus(MapStatusUpdateFactory.newMapStatus(
                MapStatus.Builder()
                        .target(LatLng(23.135288368164, 113.27128276473))
                        .zoom(10.0F).build()))
    }

    //初始化定位
    private fun initLoc() {
        //注册定位监听器
        map.isMyLocationEnabled = true
        locClient.registerLocationListener(locationListener)
        val option = LocationClientOption()
        option.isOpenGps = true//开gps
        option.coorType = "bd09ll"//设置坐标类型
        option.scanSpan = 5000//扫描速度
        locClient.locOption = option
        locClient.start()
        Logger.i("开启定位")
        map.setMyLocationConfiguration(MyLocationConfiguration(MyLocationConfiguration.LocationMode.NORMAL, true, BitmapDescriptorFactory.fromResource(R.drawable.my_location_point)))
        initView()//初始化视图
    }

    //初始化视图
    private fun initView() {
        gotoMyLoc.onClick { backToMyLoc(18.0F) }

        openUnusual.onClick {
            isUnusualShowing = !isUnusualShowing
            if (isUnusualShowing) {
                openUnusual.unusualImageView.imageResource = R.drawable.cancel_unusual
                //TODO 显示异常点
            } else {
                openUnusual.unusualImageView.imageResource = R.drawable.open_unusual
                //TODO 去除异常点
            }
        }


        chartDialog = ChartDialog(this@MainActivity) {
            onDismiss { showToast("消失了") }
            onCancel { showToast("取消") }
            onChartClick {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val intent = Intent(this@MainActivity, ChartActivity::class.java)
                    intent.putExtra("time", time)
                    this@MainActivity.startActivity(intent, ActivityOptions.makeSceneTransitionAnimation(this@MainActivity, chart, "chartTransition").toBundle())
                } else {
                    val intent = Intent(this@MainActivity, ChartActivity::class.java)
                    intent.putExtra("time", time)
                    this@MainActivity.startActivity(intent)
                }
            }

            onDetail {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    val intent = Intent(this@MainActivity, ChartActivity::class.java)
                    intent.putExtra("time", time)
                    this@MainActivity.startActivity(intent, ActivityOptions.makeSceneTransitionAnimation(this@MainActivity, chart, "chartTransition").toBundle())
                } else {
                    val intent = Intent(this@MainActivity, ChartActivity::class.java)
                    intent.putExtra("time", time)
                    this@MainActivity.startActivity(intent)
                }
            }
        }

        searchTextView.setAdapter(suggestAdapter)
        searchTextView.threshold = 1
        searchTextView.dropDownVerticalOffset = 5
        suggestSearch.setOnGetSuggestionResultListener {
            if (it == null || it.allSuggestions == null) return@setOnGetSuggestionResultListener
            suggestAdapter = SearchItemAdapter(this, it.allSuggestions.map { it.key })
            searchTextView.setAdapter(suggestAdapter)
            suggestAdapter?.notifyDataSetChanged()
        }
        //调整drop down宽度
        searchTextView.onTouch { v, event ->
            showToast("click")
            searchTextView.dropDownWidth = searchCardView.width
        }
        searchTextView.setOnItemClickListener { adapterView, view, i, l ->
            searchTextView.clearFocus()
        }
        searchTextView.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(p0: Editable?) {

            }

            override fun beforeTextChanged(p0: CharSequence, p1: Int, p2: Int, p3: Int) {

            }

            override fun onTextChanged(p0: CharSequence, p1: Int, p2: Int, p3: Int) {
                if (p0.isEmpty()) return
                suggestSearch
                        .requestSuggestion(SuggestionSearchOption().city("广州").citylimit(true).keyword(p0.toString()))
            }
        })
        searchButton.onClick {
            val info: String = searchTextView.text.toString()
            if (info.isEmpty()) {
                showToast("搜索内容为空")
                return@onClick
            }
            poiSearch.searchInCity(PoiCitySearchOption().city("广州").keyword(info))

        }
        poiSearch.setOnGetPoiSearchResultListener(object : OnGetPoiSearchResultListener {
            override fun onGetPoiIndoorResult(p0: PoiIndoorResult?) {

            }

            override fun onGetPoiResult(p0: PoiResult?) {
                if (p0 == null || p0.error == SearchResult.ERRORNO.RESULT_NOT_FOUND) {
                    showToast("未找到结果")
                    return
                }
                if (p0.error == SearchResult.ERRORNO.NO_ERROR) {
                    map.clear()
                    map.addOverlays(p0.allPoi.map { MarkerOptions().position(it.location).icon(poimarkerIcon) })
                }
            }

            override fun onGetPoiDetailResult(p0: PoiDetailResult?) {

            }
        })
    }

    private fun initMap() {
        MapView.setMapCustomEnable(true)//设置个性化
        map.setOnMapStatusChangeListener(mapStateChangeListener)//地图状态变化监听
        map.setOnMapClickListener(onMapClickListener)//地图点击监听
        map.setOnMapLoadedCallback {
            Logger.i("地图加载完成")
            map.setMapStatusLimits(map.mapStatus.bound)
        }
        mapView.showZoomControls(false)
    }

    //回到定位位置
    private fun backToMyLoc(zoom: Float) {
        map.animateMapStatus(MapStatusUpdateFactory.newMapStatus(
                MapStatus.Builder()
                        .target(LatLng(curPoint.latitude, curPoint.longitude))
                        .zoom(zoom).build()))
    }

    @Subscribe
    fun addHeatMap(list: LatLongList) {
        if (list.option.isEmpty()) return
        heatMap = HeatMap.Builder().data(list.option).build()
    }

    override fun onResume() {
        mapView.onResume()
        locClient.registerLocationListener(locationListener)
        Logger.i("注册定位监听")
        super.onResume()
    }

    override fun onDestroy() {
        locClient.stop()// 退出时销毁定位
        map.isMyLocationEnabled = false// 关闭定位图层
        mapView.onDestroy()
        super.onDestroy()
    }

    override fun onPause() {
        locClient.unRegisterLocationListener(locationListener)
        Logger.i("注销定位监听")
        Http.getInstance().cancelCall(Http.TAG_HEATPOINTS)
        mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        locClient.unRegisterLocationListener(locationListener)
        Logger.i("注销定位监听")
        EventBus.getDefault().unregister(this)//取消订阅
        super.onStop()
    }

    override fun onBackPressed() {
        if (chartDialog!!.isShowing) {
            chartDialog?.dismiss()
        } else {
            super.onBackPressed()
        }
    }

}