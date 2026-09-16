package by.glitchlab.orders

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class MainActivity : android.app.Activity() {
    private val exec=Executors.newSingleThreadExecutor()
    private val prefs by lazy { getSharedPreferences("auth",Context.MODE_PRIVATE) }
    private val apiBase get() = prefs.getString("api","https://YOUR-DOMAIN.TLD/api.php")!!
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private var token: String? = null
    private val bg=Color.rgb(7,10,13); private val panel=Color.rgb(14,20,26); private val line=Color.rgb(34,42,49); private val text=Color.rgb(242,244,245); private val muted=Color.rgb(139,148,155); private val blue=Color.rgb(22,143,232); private val green=Color.rgb(0,184,117); private val red=Color.rgb(239,35,55); private val orange=Color.rgb(243,165,59)

    override fun onCreate(b: Bundle?) { super.onCreate(b); token=prefs.getString("token",null); showLoginIfNeeded() }
    private fun showLoginIfNeeded(){ if(token==null) loginScreen() else dashboard() }
    private fun base(): LinearLayout { root=LinearLayout(this); root.orientation=LinearLayout.VERTICAL; root.setBackgroundColor(bg); return root }
    private fun tv(s:String,size:Float,color:Int= text): TextView=TextView(this).apply{ text=s; textSize=size; setTextColor(color); setPadding(0,0,0,0) }
    private fun button(s:String,on:()->Unit): Button=Button(this).apply{ text=s; setTextColor(this@MainActivity.text); setOnClickListener{on()}; isAllCaps=false; setBackgroundColor(panel) }
    private fun loginScreen(){ base(); val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(36,70,36,30)}; box.addView(tv("glitchLab",30f)); box.addView(tv("Личный учёт заказов",14f,muted), lp(0,0,0,10)); val url=EditText(this).apply{hint="https://ваш-домен.by/api.php";setText(prefs.getString("api", ""));setTextColor(this@MainActivity.text);setHintTextColor(muted)}; val login=EditText(this).apply{hint="Логин";setTextColor(this@MainActivity.text);setHintTextColor(muted)}; val pass=EditText(this).apply{hint="Пароль";setTextColor(this@MainActivity.text);setHintTextColor(muted);inputType=0x81}; val go=button("Войти"){ prefs.edit().putString("api",url.text.toString().trim()).apply(); exec.execute{ try{ val j=req("login",JSONObject().put("login",login.text.toString()).put("password",pass.text.toString()),false); val t=j.getJSONObject("data").getString("token"); prefs.edit().putString("token",t).apply(); runOnUiThread{token=t;dashboard()} }catch(e:Exception){runOnUiThread{Toast.makeText(this,"Ошибка входа: ${e.message}",Toast.LENGTH_LONG).show()}} } }; box.addView(tv("Адрес API",11f,muted));box.addView(url,lp(-1,55,0,10));box.addView(login,lp(-1,55,0,8));box.addView(pass,lp(-1,55,0,14));box.addView(go,lp(-1,52,0,0));root.addView(box,LinearLayout.LayoutParams(-1,-1));setContentView(root)}
    private fun dashboard(){ base(); val head=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL;setPadding(20,20,20,12)}; val title=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}; title.addView(tv("glitchLab",23f)); title.addView(tv("ЗАКАЗЫ",10f,muted)); head.addView(title,LinearLayout.LayoutParams(0,-2,1f)); head.addView(button("+ Заказ"){showAddOrder()}); head.addView(button("↻"){load()}); head.addView(button("Выйти"){prefs.edit().remove("token").apply();token=null;loginScreen()}); root.addView(head); content=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(14,0,14,24)}; val scroll=ScrollView(this);scroll.addView(content);root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f)); load() }
    private fun showAddOrder(){
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(28,10,28,10)}
        val name=EditText(this).apply{hint="Имя клиента (необязательно)";setTextColor(this@MainActivity.text);setHintTextColor(muted)}
        val phone=EditText(this).apply{hint="Телефон (необязательно)";setTextColor(this@MainActivity.text);setHintTextColor(muted);inputType=3}
        val problem=EditText(this).apply{hint="Что делал / описание заказа";setTextColor(this@MainActivity.text);setHintTextColor(muted);minLines=3;gravity=Gravity.TOP}
        val type=Spinner(this).apply{adapter=ArrayAdapter(this@MainActivity,android.R.layout.simple_spinner_dropdown_item,arrayOf("Диагностика","Ремонт","Чистка и охлаждение","Windows / ПО","Апгрейд","Сборка ПК","Другое"))}
        val amount=EditText(this).apply{hint="Сумма BYN (можно позже)";setTextColor(this@MainActivity.text);setHintTextColor(muted);inputType=2 or 8192}
        box.addView(name,lp(-1,55,0,8)); box.addView(phone,lp(-1,55,0,8)); box.addView(problem,lp(-1,110,0,8)); box.addView(type,lp(-1,52,0,8)); box.addView(amount,lp(-1,55,0,14))
        val dialog=android.app.AlertDialog.Builder(this).setTitle("Новый заказ").setView(box).setNegativeButton("Отмена",null).setPositiveButton("Добавить",null).create()
        dialog.setOnShowListener{ dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            val ph=phone.text.toString().trim(); val pr=problem.text.toString().trim(); val nm=name.text.toString().trim(); val am=amount.text.toString().trim().replace(',','.')
            if(ph.isBlank() && nm.isBlank()){ Toast.makeText(this,"Укажи имя или телефон",Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            if(pr.isBlank()){ Toast.makeText(this,"Укажи описание заказа",Toast.LENGTH_SHORT).show(); return@setOnClickListener }
            exec.execute{try{
                val body=JSONObject().put("name",nm).put("phone",ph).put("problem",pr).put("type",type.selectedItem.toString())
                val created=req("create_app",body,true)
                val id=created.getJSONObject("data").getInt("id")
                if(am.isNotBlank()) req("update",JSONObject().put("id",id).put("status","new").put("amount",am.toDouble()),true)
                runOnUiThread{dialog.dismiss();Toast.makeText(this,"Заказ #$id добавлен",Toast.LENGTH_SHORT).show();load()}
            }catch(e:Exception){runOnUiThread{Toast.makeText(this,"Ошибка: ${e.message}",Toast.LENGTH_LONG).show()}}}
        }}
        dialog.show()
    }
    private fun load(){ runOnUiThread{content.removeAllViews();content.addView(tv("Загрузка…",13f,muted),lp(-1,50,0,0))}; exec.execute{try{val st=req("stats",null,true).getJSONObject("data").getJSONObject("stats");val orders=req("orders",null,true).getJSONObject("data").getJSONArray("orders");runOnUiThread{render(st,orders)}}catch(e:Exception){runOnUiThread{Toast.makeText(this,"Ошибка: ${e.message}",Toast.LENGTH_LONG).show()}}}}
    private fun render(st:JSONObject,orders:JSONArray){content.removeAllViews(); val stats=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL}; val arr=listOf("Заказов" to st.getDouble("total"),"Выполнено" to st.getDouble("done"),"Выручка" to st.getDouble("revenue"));arr.forEach{(n,v)->val c=LinearLayout(this@MainActivity).apply{orientation=LinearLayout.VERTICAL;setPadding(14,12,8,12);setBackgroundColor(panel);addView(tv(n,10f,muted));addView(tv(if(n=="Выручка")"%.0f BYN".format(v) else "%.0f".format(v),20f))};stats.addView(c,LinearLayout.LayoutParams(0,78,1f).apply{setMargins(4,4,4,10)})};content.addView(stats);content.addView(tv("Заявки",19f),lp(-1,35,0,8));for(i in 0 until orders.length()){val o=orders.getJSONObject(i);content.addView(orderCard(o),lp(-1,-2,0,8))}}
    private fun orderCard(o:JSONObject):View{val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(16,14,16,14);setBackgroundColor(panel)};val name=o.optString("name","Не указано");val phone=o.optString("phone");val problem=o.optString("problem");val type=o.optString("type","Другое");box.addView(tv("#${o.getInt("id")}  $name",17f));box.addView(tv(type,11f,green));box.addView(tv(problem,13f,muted),lp(-1,-2,0,6));val row=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL};val status=Spinner(this);val labels=arrayOf("Новая","В работе","Выполнена","Отклонена");status.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,labels);status.setSelection(mapStatus(o.getString("status")));row.addView(status,LinearLayout.LayoutParams(0,48,1f));val amount=EditText(this).apply{hint="Сумма BYN";setTextColor(this@MainActivity.text);setHintTextColor(muted);inputType=2 or 8192;setText(if(o.isNull("amount"))"" else o.getDouble("amount").toString())};row.addView(amount,LinearLayout.LayoutParams(0,48,1f));box.addView(row);val actions=LinearLayout(this).apply{gravity=Gravity.CENTER_VERTICAL};actions.addView(button("Позвонить"){startActivity(Intent(Intent.ACTION_DIAL,Uri.parse("tel:$phone")))},LinearLayout.LayoutParams(0,48,1f));actions.addView(button("Сохранить"){val st=mapStatusBack(status.selectedItemPosition);val a=amount.text.toString().replace(',','.');exec.execute{try{req("update",JSONObject().put("id",o.getInt("id")).put("status",st).put("amount",if(a.isBlank())JSONObject.NULL else a.toDouble()),true);runOnUiThread{Toast.makeText(this,"Сохранено",Toast.LENGTH_SHORT).show();load()}}catch(e:Exception){runOnUiThread{Toast.makeText(this,"Ошибка: ${e.message}",Toast.LENGTH_LONG).show()}}}},LinearLayout.LayoutParams(0,48,1f));box.addView(actions);return box}
    private fun mapStatus(s:String)=when(s){"new"->0;"in_progress"->1;"done"->2;else->3};private fun mapStatusBack(i:Int)=arrayOf("new","in_progress","done","rejected")[i]
    private fun req(action:String,body:JSONObject?,auth:Boolean):JSONObject{val u=URL(apiBase+"?action="+URLEncoder.encode(action,"UTF-8"));val c=u.openConnection() as HttpURLConnection;c.requestMethod="POST";c.connectTimeout=10000;c.readTimeout=15000;c.setRequestProperty("Accept","application/json");if(auth)c.setRequestProperty("Authorization","Bearer "+prefs.getString("token",token));if(body!=null){c.doOutput=true;c.setRequestProperty("Content-Type","application/json; charset=utf-8");c.outputStream.use{it.write(body.toString().toByteArray())}};val r=(if(c.responseCode<400)c.inputStream else c.errorStream).bufferedReader().readText();c.disconnect();val j=JSONObject(r);if(!j.optBoolean("ok"))throw Exception(j.optString("message","Ошибка API"));return j}
    private fun lp(w:Int,h:Int,b:Int,t:Int)=LinearLayout.LayoutParams(w,h).apply{setMargins(0,t,0,b)}
}
