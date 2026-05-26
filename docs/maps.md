# 高德地图 Android 轻量版地图 SDK 完整文档

> 来源文档：高德开放平台 | 轻量版地图SDK (Android)
> 抓取时间：2026-05-19

---

## 目录索引

1. [概述](#概述)
2. [获取Key](#获取key)
3. [Android Studio配置工程](#android-studio配置工程)
4. [开发者注意事项](#开发者注意事项)
5. [显示地图](#显示地图)
6. [显示定位蓝点](#显示定位蓝点)
7. [切换地图图层](#切换地图图层)
8. [手势交互](#手势交互)
9. [调用方法交互](#调用方法交互)
10. [绘制点标记](#绘制点标记)
11. [绘制折线](#绘制折线)
12. [绘制面](#绘制面)
13. [点平滑移动](#点平滑移动)
14. [坐标转换](#坐标转换)
15. [距离/面积计算](#距离面积计算)
16. [更新日志](#更新日志)
17. [相关下载](#相关下载)

---

# 概述

> 来源：https://lbs.amap.com/api/lightweight-android-sdk/summary

**最后更新时间：2021年03月10日**

## 简介

基于用户对现有地图SDK包体过大、功能冗余的反馈，开发轻量化地图SDK，以减轻包体负担并提供基础且常用的地图功能。

**JS版本说明**

使用JS API 2.0，JS API的更新由轻量版SDK控制。

## 兼容性

Android 轻量版地图SDK 1.1.0，支持 Android4.0以上系统。

---

# 获取Key

> 来源：https://lbs.amap.com/api/lightweight-android-sdk/gettingstarted/get-key

**最后更新时间：2021年03月10日**

## 如何申请Key

### 1、创建应用

进入高德开放平台[控制台](https://lbs.amap.com/dev/)，创建一个新应用。如果您之前已经创建过应用，可直接跳过这个步骤。

### 2、添加新Key

在创建的应用上点击"添加"按钮，在弹出的对话框中，依次输入key名称，选择绑定的服务为"Android平台SDK"，输入发布版安全码 SHA1、调试版安全码 SHA1、以及 Package。

> **需要注意的是：1个KEY只能用于一个应用（多渠道安装包属于多个应用），1个Key在多个应用上使用会出现服务调用失败。**

## 如何获取SHA1

调试版本（debug）和发布版本（release）下的 SHA1 值是不同的，发布 apk 时需要根据发布 apk 对应的 keystore 重新配置 Key。

### 通过Eclipse获取 SHA1

使用 adt 22 以上版本，可以在 eclipse 中直接查看。

- **Windows**：依次在 eclipse 中打开 Window -> Preferances -> Android -> Build。
- **Mac**：依次在 eclipse 中打开 Eclipse/ADT -> Preferances -> Android -> Build。

在弹出的 Build 对话框中 "SHA1 fingerprint" 中的值即为 Android 签名证书的 SHA1 值。

### 使用 keytool（jdk自带工具）获取 SHA1

1. 打开终端命令行工具

2. 输入命令：

   ```
   keytool -v -list -keystore <keystore文件路径>
   ```

3. 输入 Keystore 密码

> 说明：keystore 文件为 Android 签名证书文件。提示输入密钥库密码，开发模式默认密码是 android，发布模式的密码是为 apk 的 keystore 设置的密码。

## 如何获取 PackageName

打开 Android 项目的 AndroidManifest.xml 配置文件，package 属性所对应的内容为应用包名。

也请检查 build.gradle 文件的 applicationid 属性是否与上文提到的 package 属性一致，如果不一致会导致 INVALID_USER_SCODE，请调整一致。

---

# Android Studio配置工程

> 来源：https://lbs.amap.com/api/lightweight-android-sdk/gettingstarted/android-studio-create-project

**最后更新时间：2021年03月10日**

## 新建一个Android工程

新建一个 Empty Activity 应用项目，可参考入门指南"创建工程"章节创建一个Android工程。

## 通过Gradle集成SDK

轻量版地图SDK推荐使用Gradle集成方式。

### 1、在Project的build.gradle文件中配置repositories

Android Studio默认会在Project的build.gradle为所有module自动添加jcenter的仓库地址，如果已存在，则不需要重复添加。

配置如下：

```java
allprojects {
    repositories {
        jcenter() // 或者 mavenCentral()
    }
 }
```

### 2、在主工程的build.gradle文件配置dependencies

根据项目需求添加SDK依赖。轻量版地图SDK对应的依赖为：

| SDK              | 引入代码                                        |
| ---------------- | ----------------------------------------------- |
| 2D地图（轻量版） | compile 'com.amap.api:map2d:latest.integration' |

主工程的build.gradle文件配置示例：

```java
dependencies {
    compile fileTree(dir: 'libs', include: ['*.jar'])
    //2D地图（轻量版地图SDK）
    compile 'com.amap.api:map2d:latest.integration'
}
```

如需引入指定版本SDK，如下所示：

```java
dependencies {
    compile fileTree(dir: 'libs', include: ['*.jar'])
    compile 'com.amap.api:map2d:5.0.0'
}
```

### 3、配置AndroidManifest.xml

在AndroidManifest.xml中添加所需的权限（网络权限、SD卡读写权限等）和Key。

```xml
<!-- 地图SDK需要的权限 -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />

<!-- 设置高德Key -->
<meta-data
    android:name="com.amap.api.v2.apikey"
    android:value="您的高德Key" />
```

## 注意事项

1. 轻量版地图SDK为2D地图，无需添加so库。
2. 如果build失败提示com.amap.api:XXX:X.X.X找不到，请确认拼写及版本号是否正确，如果访问不到jcenter可以切换为maven仓库尝试一下。
3. 依照Gradle方式引入SDK以后，不需要在libs文件夹下导入对应SDK的jar包，会有冲突。

---

# 开发者注意事项

> 来源：https://lbs.amap.com/api/lightweight-android-sdk/gettingstarted/dev-attention

**最后更新时间：2025年05月12日**

## 添加高德Key

为了保证高德Android SDK的功能正常使用，您需要申请高德Key并且配置到项目中。

项目的 "AndroidManifest.xml" 文件中，添加如下代码：

```xml
<application
         android:icon="@drawable/icon"
         android:label="@string/app_name" >
         <meta-data
            android:name="com.amap.api.v2.apikey"
            android:value="请输入您的用户Key"/>
            ……
</application>
```

## 配置权限

在AndroidManifest.xml中配置权限：

```xml
<!--允许访问网络，必选权限-->
<uses-permission android:name="android.permission.INTERNET" />  
 
<!--允许获取网络状态-->
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />    

<!--允许获取wifi网络信息-->
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" /> 

<!--允许写设备缓存，用于问题排查-->
<uses-permission android:name="android.permission.WRITE_SETTINGS" />  
```

## 个人及设备信息采集设置

```java
/**
 * 基础库设置是否允许采集个人及设备信息
 * @param collectEnable: true 允许采集 false 不允许采集 
 */
AMapUtilCoreApi.setCollectInfoEnable(boolean collectEnable);
```

## 代码混淆

在生成apk进行代码混淆时进行如下配置（如果报出warning，在报出warning的包加入类似的语句：-dontwarn 包名）：

```
3D 地图 V5.0.0之前：
-keep   class com.amap.api.maps.**{*;} 
-keep   class com.autonavi.amap.mapcore.*{*;} 
-keep   class com.amap.api.trace.**{*;}

3D 地图 V5.0.0之后：
-keep   class com.amap.api.maps.**{*;} 
-keep   class com.autonavi.**{*;} 
-keep   class com.amap.api.trace.**{*;}

定位
-keep class com.amap.api.location.**{*;}
-keep class com.amap.api.fence.**{*;}
-keep class com.autonavi.aps.amapapi.model.**{*;}

搜索
-keep   class com.amap.api.services.**{*;}

2D地图（轻量版适用）
-keep class com.amap.api.maps2d.**{*;}
-keep class com.amap.api.mapcore2d.**{*;}

导航
-keep class com.amap.api.navi.**{*;}
-keep class com.autonavi.**{*;}
```

## so文件说明

地图SDK的核心功能实现依赖so库。在使用SDK，以及向工程中添加so时请注意以下几点：

### 确保添加了正确的so库文件

官方发布新版SDK时一定会同时更新jar文件和so文件，您需要做的是更新这些文件到您的工程中，不要出现遗漏。

### 确保添加的so库文件与平台匹配

arm与x86，这代表核心处理器（cpu）的两种架构，对不同的架构需要引用不同的so文件，如果引用出现错误是不能正常使用SDK的。解决这个问题最简单的办法是在libs或jnilibs文件夹下只保留armeabi一个文件夹。

> 注意：轻量版地图SDK（2D地图）无需so库，此说明适用于3D地图和导航SDK。

### 其余问题

您也可以浏览[高德论坛相关帖子](https://lbsbbs.amap.com/forum.php?mod=viewthread&tid=14693)的内容，基础的开发技能均在其中。

---

# 显示地图

> 来源：https://lbs.amap.com/api/lightweight-android-sdk/guide/create-map/show-map

**最后更新时间：2021年03月10日**

使用地图SDK之前，需要在 AndroidManifest.xml 文件中进行相关权限设置，确保地图功能可以正常使用。

## 第一步，配置AndroidManifest.xml

### 首先，声明权限

```xml
<!--地图SDK（包含其搜索功能）需要的基础权限-->
     
<!--允许程序打开网络套接字-->
<uses-permission android:name="android.permission.INTERNET" />
<!--允许程序设置内置sd卡的写权限-->
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />   
<!--允许程序获取网络状态-->
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" /> 
<!--允许程序访问WiFi网络信息-->
<uses-permission android:name="android.permission.ACCESS_WIFI_STATE" />   
<!--允许程序访问CellID或WiFi热点来获取粗略的位置-->
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

### 然后，设置高德Key

在application标签中加入如下内容：

```xml
<meta-data android:name="com.amap.api.v2.apikey" android:value="key">
//开发者申请的key  
</meta-data>
```

> [点我获取Key](https://developer.amap.com/dev/#/)
> [点我查看Key注册时必要数据SHA1和包名的获取方法](https://developer.amap.com/faq/top/hot-questions/249)

## 第二步，向工程中添加地图开发包

将jar包放入libs目录下，依次添加依赖。

```java
dependencies {
    implementation files("libs/Android_Lite3DMap_SDK_V1.1.0_20210201.jar")
    //...
}
```

或者直接使用引入libs下所有jar包的方式：

```java
dependencies {
    implementation fileTree(dir: "libs", include: ["*.jar"])
    //...
}
```

## 第三步，初始化地图容器

### 首先，准备WebView

WebView 是Android系统提供的View，也可以是用户自定义的WebView如UCWebView，用于在 Android View 中放置地图。WebView 是地图容器。用 WebView 加载地图的方法与 Android 提供的其他 View 一样。

注意：以下示例均用Android系统的WebView来实现，其他自定义WebView可以参考实现。

在布局文件中添加WebView：

```xml
<com.amap.maps.jsmap.demo.webview.MyWebView
    android:id="@+id/webview"
    android:layout_width="match_parent"
    android:layout_height="match_parent" />
```

为了保证功能正常运行，WebView需要打开部分开关。在项目中MyWebView集成自WebView：

```java
public class MyWebView extends WebView {
    public MyWebView(Context context) {
        this(context, null);
    }

    public MyWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initBridgeWebView(context, attrs);
    }

    public MyWebView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initBridgeWebView(context, attrs);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void initBridgeWebView(Context context, AttributeSet attrs) {
        WebSettings settings = getSettings();
        //允许使用js
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        settings.setDefaultTextEncodingName("utf-8");
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT){
            setWebContentsDebuggingEnabled(true);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            settings.setAllowUniversalAccessFromFileURLs(true);
        }
        settings.setAllowFileAccess(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            settings.setAllowFileAccessFromFileURLs(true);
        }
        settings.setAllowContentAccess(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        setWebChromeClient(new WebChromeClient());
    }
}
```

### 然后，创建IAMapWebView桥接

IAMapWebView.java 中包含了SDK内部需要用到WebView的接口，这一步非常重要，如果不实现地图将无法展示。

```java
public class MAWebViewWrapper implements IAMapWebView {

    private final WebView webView;
    private WebViewClient mapWebViewClient;

    public MAWebViewWrapper(final WebView webView) {
        this.webView = webView;
        if (this.webView != null) {
            this.webView.setWebViewClient(
                    new WebViewClient() {
                        @TargetApi(Build.VERSION_CODES.N)
                        @Override
                        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                            if (mapWebViewClient != null) {
                                boolean flag = mapWebViewClient.shouldOverrideUrlLoading(view, request);
                                if (flag) return true;
                            }
                            return super.shouldOverrideUrlLoading(view, request);
                        }

                        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                        @Override
                        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                            if (mapWebViewClient != null) {
                                WebResourceResponse flag = mapWebViewClient.shouldInterceptRequest(view, request);
                                if (flag != null) return flag;
                            }
                            return super.shouldInterceptRequest(view, request);
                        }

                        @Override
                        public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                            if (mapWebViewClient != null) {
                                WebResourceResponse flag = mapWebViewClient.shouldInterceptRequest(view, url);
                                if (flag != null) return flag;
                            }
                            return super.shouldInterceptRequest(view, url);
                        }
                    }
            );
        }
    }

    @Override
    public void evaluateJavascript(String jsCallSig, ValueCallback<String> callback) {
        if (this.webView != null) this.webView.evaluateJavascript(jsCallSig, callback);
    }

    @Override
    public void loadUrl(String toString) {
        if (this.webView != null) this.webView.loadUrl(toString);
    }

    @Override
    public void addAMapJavascriptInterface(IAMapJsCallback object, String javascriptInterfaceName) {
        if (this.webView != null) this.webView.addJavascriptInterface(object, javascriptInterfaceName);
    }

    @Override
    public void setWebViewClient(WebViewClient webViewClient) {
        this.mapWebViewClient = webViewClient;
    }

    @Override
    public int getWidth() {
        if (this.webView != null) return this.webView.getWidth();
        return 0;
    }

    @Override
    public int getHeight() {
        if (this.webView != null) this.webView.getHeight();
        return 0;
    }

    @Override
    public void addView(View v, ViewGroup.LayoutParams params) {
        if (webView != null && v != null) webView.addView(v, params);
    }
}
```

### 接着，创建地图控制对象

创建地图时使用AMapWrapper：

```java
AMapWrapper aMapWrapper = new AMapWrapper(this, webViewWrapper);
```

> **注意**：如果为了缩短启动时间，可以在后台先执行到这一步，后续的步骤可以在需要展示地图的时候才执行。

### 最后，初始化地图并获取AMap对象

```java
aMapWrapper.onCreate();
aMapWrapper.getMapAsyn(new AMap.OnMapReadyListener() {
    @Override
    public void onMapReady(AMap map) {
        //todo
    }
});
```

至此就可以看到地图展示，并且在onMapReady中拿到了AMap对象后，就可以往地图上添加点线面等覆盖物。

---

# 显示定位蓝点

> 来源：https://lbs.amap.com/api/lightweight-android-sdk/guide/create-map/mylocation

## 第一步，初始化地图

初始化 aMap 对象，设置以下定位相关内容：

```java
// 设置定位监听
aMap.setLocationSource(this);
// 设置为true表示显示定位层并可触发定位，false表示隐藏定位层并不可触发定位，默认是false
aMap.setMyLocationEnabled(true);
// 设置定位的类型为定位模式，有定位、跟随或地图根据面向方向旋转几种
aMap.setMyLocationType(AMap.LOCATION_TYPE_LOCATE);
```

## 第二步，初始化定位

在aMap.setLocationSource(this)中包含两个回调，activate(OnLocationChangedListener)和deactivate()。

在activate()中设置定位初始化及启动定位，在deactivate()中写停止定位的相关调用。

```java
OnLocationChangedListener mListener;
AMapLocationClient mlocationClient;
AMapLocationClientOption mLocationOption;
/**
 * 激活定位
 */
@Override
public void activate(OnLocationChangedListener listener) {
    mListener = listener;
    if (mlocationClient == null) {
        //初始化定位
        mlocationClient = new AMapLocationClient(this);
        //初始化定位参数
        mLocationOption = new AMapLocationClientOption();
        //设置定位回调监听
        mlocationClient.setLocationListener(this);
        //设置为高精度定位模式
        mLocationOption.setLocationMode(AMapLocationMode.Hight_Accuracy);
        //设置定位参数
        mlocationClient.setLocationOption(mLocationOption);
        mlocationClient.startLocation();//启动定位
    }
}
/**
 * 停止定位
 */
@Override
public void deactivate() {
    mListener = null;
    if (mlocationClient != null) {
        mlocationClient.stopLocation();
        mlocationClient.onDestroy();
    }
    mlocationClient = null;
}
```

## 第三步，在定位回调中设置显示定位小蓝点

定位回调方法：onLocationChanged(AMapLocation amapLocation)。

在回调方法中调用"mListener.onLocationChanged(amapLocation);"可以在地图上显示系统小蓝点。

```java
/**
 * 定位成功后回调函数
 */
@Override
public void onLocationChanged(AMapLocation amapLocation) {
    if (mListener != null && amapLocation != null) {
        if (amapLocation != null
                && amapLocation.getErrorCode() == 0) {
            mListener.onLocationChanged(amapLocation);// 显示系统小蓝点
        } else {
            String errText = "定位失败," + amapLocation.getErrorCode()+ ": " + amapLocation.getErrorInfo();
            Log.e("AmapErr",errText);
        }
    }
}
```

> **注意**：需要在系统 onDestroy() 方法中销毁定位对象。

```java
@Override
protected void onDestroy() {
    super.onDestroy();
    mapView.onDestroy();
    if(null != mlocationClient){
        mlocationClient.onDestroy();
    }
}
```

---

# 更新日志

> **来源：** https://lbs.amap.com/api/lightweight-android-sdk/changelog  
> **最后更新时间：** 2024年03月15日

## V1.3.2（2024-03-15）

- 合规问题优化，支持关闭 oaid 采集

## V1.3.1（2023-08-15）

- 合规问题修改

## V1.2.0（2021-04-13）

- 修复部分级别下，地图展示白屏问题

## V1.1.0（2021-03-15）

- 首次发布：支持实现基础地图功能，包括不同类型的地图、与地图的交互、地图覆盖物绘制和计算工具等

---

# 相关下载

> **来源：** https://lbs.amap.com/api/lightweight-android-sdk/download  
> **最后更新时间：** 2026年04月17日

## 一键下载

包括：

- **轻量版地图包**：轻量版地图SDK V1.3.2（包名：com.amap.maps.jsmap）
- **搜索SDK** V9.7.4（包名：com.amap.api.search）
- **定位SDK** V6.4.9（包名：com.amap.api.location）
- 示例代码、开发文档

MD5码：`8dbdd267ac70d7826983166621718b26`

## 合包下载

根据开发场景选择合包，合包包含多个产品功能，自动合并代码，大大减少了包体积。

可选合包类型：

- 3D地图合包
- **轻量版地图合包**
- 导航合包
- 定位包
- 猎鹰合包

### 轻量版地图合包详情

| 项目          | 信息                                     |
| ------------- | ---------------------------------------- |
| 轻量版地图SDK | V1.2.0（包名：com.amap.api.jsmap）       |
| 搜索SDK       | V9.7.4（包名：com.amap.api.search）      |
| 定位SDK       | V11.1.200（包名：com.amap.api.location） |
| 包体积        | 3.76MB                                   |
| 更新时间      | 2026-04-24                               |
| MD5码         | `859e6330bdd0b7e6e14a1d6e8a0af210`       |

## 示例代码

- 轻量版地图基础能力示例代码

## 参考文档下载

| 文档                     | 说明                       | 更新时间   |
| ------------------------ | -------------------------- | ---------- |
| Android 平台全部开发文档 | —                          | —          |
| POI分类编码表            | 查询不同POI类型对应的编码  | 2023-10-13 |
| 城市编码表               | 查询不同城市对应的编码     | 2026-01-07 |
| 海外城市编码表           | 查询海外不同城市对应的编码 | 2026-04-30 |