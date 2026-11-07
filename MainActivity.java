package com.footballanalyzer.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final int BG = Color.rgb(8,18,14), PANEL = Color.rgb(17,34,26);
    private final int GREEN = Color.rgb(56,216,120), WHITE = Color.WHITE, MUTED = Color.rgb(166,187,174);
    private EditText teamA, teamB, league, apiKey;
    private TextView output, status;
    private SharedPreferences prefs;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("football_analyzer", MODE_PRIVATE);
        buildUi();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18),dp(22),dp(18),dp(28));
        scroll.addView(root);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText("⚽ FOOTBALL ANALYZER");
        title.setTextColor(GREEN); title.setTextSize(25); title.setTypeface(null,Typeface.BOLD);
        root.addView(title);
        TextView sub = new TextView(this);
        sub.setText("Реальная статистика матчей • API-Football");
        sub.setTextColor(MUTED); sub.setTextSize(13);
        LinearLayout.LayoutParams sp = params(-1,-2); sp.bottomMargin=dp(20); root.addView(sub,sp);

        section(root,"НАСТРОЙКА API");
        apiKey = field("Вставь API-Football ключ", true);
        apiKey.setText(prefs.getString("api_key",""));
        root.addView(apiKey,params(-1,-2));
        Button save = button("СОХРАНИТЬ КЛЮЧ");
        root.addView(save,params(-1,-2));
        save.setOnClickListener(v -> {
            String k=apiKey.getText().toString().trim();
            if(k.isEmpty()){ toast("Сначала вставь API-ключ"); return; }
            prefs.edit().putString("api_key",k).apply();
            toast("Ключ сохранён на этом телефоне");
        });
        Button test = button("ПРОВЕРИТЬ ПОДКЛЮЧЕНИЕ");
        root.addView(test,params(-1,-2));
        test.setOnClickListener(v -> checkApi());

        section(root,"КОМАНДЫ И МАТЧ");
        teamA=field("Первая команда (например, Arsenal)",false); root.addView(teamA,params(-1,-2));
        teamB=field("Вторая команда (например, Chelsea)",false); root.addView(teamB,params(-1,-2));
        league=field("Лига (необязательно, например Premier League)",false); root.addView(league,params(-1,-2));
        Button analyze=button("АНАЛИЗИРОВАТЬ МАТЧ");
        LinearLayout.LayoutParams ap=params(-1,-2); ap.topMargin=dp(8); root.addView(analyze,ap);
        analyze.setOnClickListener(v -> analyze());

        status=new TextView(this); status.setTextColor(GREEN); status.setTextSize(13);
        LinearLayout.LayoutParams st=params(-1,-2); st.topMargin=dp(14); root.addView(status,st);
        output=new TextView(this); output.setTextColor(WHITE); output.setTextSize(15);
        output.setPadding(dp(14),dp(14),dp(14),dp(14));
        output.setBackground(panelBg());
        LinearLayout.LayoutParams op=params(-1,-2); op.topMargin=dp(10); root.addView(output,op);
        output.setText("Здесь появится анализ: голы, угловые, жёлтые карточки и фолы.\n\nДля начала сохрани свой API-ключ.");
        TextView note=new TextView(this);
        note.setText("Важно: ключ хранится локально на телефоне. Не отправляй APK с личным ключом другим людям. Доступность статистики зависит от лиги и конкретного матча.");
        note.setTextColor(MUTED); note.setTextSize(11);
        LinearLayout.LayoutParams np=params(-1,-2); np.topMargin=dp(16); root.addView(note,np);
    }

    private void section(LinearLayout root,String s){
        TextView t=new TextView(this); t.setText(s); t.setTextColor(GREEN); t.setTextSize(13); t.setTypeface(null,Typeface.BOLD);
        LinearLayout.LayoutParams p=params(-1,-2); p.topMargin=dp(15); p.bottomMargin=dp(8); root.addView(t,p);
    }
    private EditText field(String hint, boolean secret){
        EditText e=new EditText(this); e.setSingleLine(true); e.setTextColor(WHITE); e.setHintTextColor(MUTED);
        e.setHint(hint); e.setTextSize(15); e.setPadding(dp(13),dp(8),dp(13),dp(8));
        e.setBackground(panelBg());
        if(secret) e.setInputType(129);
        LinearLayout.LayoutParams p=params(-1,-2); p.bottomMargin=dp(9);
        return e;
    }
    private Button button(String text){
        Button b=new Button(this); b.setText(text); b.setTextColor(Color.BLACK); b.setTextSize(13); b.setTypeface(null,Typeface.BOLD);
        b.setBackground(round(GREEN,12)); return b;
    }
    private GradientDrawable panelBg(){ return round(PANEL,12); }
    private GradientDrawable round(int color,int radius){
        GradientDrawable d=new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(radius)); return d;
    }
    private LinearLayout.LayoutParams params(int w,int h){ return new LinearLayout.LayoutParams(w,h); }
    private int dp(int x){ return (int)(x*getResources().getDisplayMetrics().density+0.5f); }
    private void toast(String s){ Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
    private String key(){ String k=apiKey.getText().toString().trim(); if(!k.isEmpty()) prefs.edit().putString("api_key",k).apply(); else k=prefs.getString("api_key",""); return k; }

    private JSONObject request(String path) throws Exception {
        String k=key(); if(k.isEmpty()) throw new Exception("Сначала введи и сохрани API-ключ.");
        URL url=new URL("https://v3.football.api-sports.io/"+path);
        HttpURLConnection c=(HttpURLConnection)url.openConnection();
        c.setRequestMethod("GET"); c.setConnectTimeout(15000); c.setReadTimeout(20000);
        c.setRequestProperty("x-apisports-key",k); c.setRequestProperty("Accept","application/json");
        int code=c.getResponseCode();
        InputStream is=(code>=200 && code<300)?c.getInputStream():c.getErrorStream();
        BufferedReader r=new BufferedReader(new InputStreamReader(is));
        StringBuilder sb=new StringBuilder(); String line; while((line=r.readLine())!=null) sb.append(line);
        r.close(); c.disconnect();
        JSONObject j=new JSONObject(sb.toString());
        if(code<200 || code>=300) throw new Exception("HTTP "+code+": "+j.optJSONArray("errors"));
        JSONArray errors=j.optJSONArray("errors");
        if(errors!=null && errors.length()>0 && !errors.toString().equals("[]")) throw new Exception("API: "+errors.toString());
        return j;
    }

    private void checkApi(){
        status.setText("Проверяю API...");
        executor.execute(() -> {
            try {
                JSONObject j=request("status");
                JSONObject response=j.optJSONObject("response");
                String msg="Подключение успешно.";
                if(response!=null) msg += "\nAPI-ключ распознан. Запросы сегодня: "+response.optJSONObject("requests").optInt("current",0);
                final String m=msg; main.post(()->status.setText(m));
            } catch(Exception e){ main.post(()->status.setText("Ошибка подключения: "+e.getMessage())); }
        });
    }

    private void analyze(){
        final String a=teamA.getText().toString().trim(), b=teamB.getText().toString().trim();
        if(a.isEmpty()||b.isEmpty()){ toast("Введи названия обеих команд"); return; }
        if(key().isEmpty()){ toast("Сначала введи API-ключ"); return; }
        output.setText("Ищу команды и последние матчи. Это может занять до минуты...");
        status.setText("Идёт анализ. Не закрывай приложение.");
        executor.execute(() -> {
            try {
                JSONObject ta=findTeam(a), tb=findTeam(b);
                int ida=ta.getInt("id"), idb=tb.getInt("id");
                JSONArray fa=lastFixtures(ida), fb=lastFixtures(idb);
                TeamStats sa=collect(ida,fa), sb=collect(idb,fb);
                JSONObject h2h=request("fixtures/headtohead?h2h="+ida+"-"+idb+"&last=5");
                int hcount=h2h.optJSONArray("response")==null?0:h2h.optJSONArray("response").length();
                String result=makeReport(ta,tb,sa,sb,hcount,fa.length(),fb.length());
                main.post(()->{output.setText(result);status.setText("Анализ завершён. Использовано последних матчей: "+fa.length()+" и "+fb.length()+".");});
            } catch(Exception e){ main.post(()->{status.setText("Не удалось завершить анализ");output.setText("Ошибка: "+e.getMessage()+"\n\nПроверь написание команд, API-ключ и доступность статистики выбранной лиги.");}); }
        });
    }

    private JSONObject findTeam(String name) throws Exception {
        JSONObject j=request("teams?search="+URLEncoder.encode(name,"UTF-8"));
        JSONArray arr=j.optJSONArray("response");
        if(arr==null||arr.length()==0) throw new Exception("Команда не найдена: "+name);
        // Prefer an exact case-insensitive name match; otherwise use the first API result.
        JSONObject chosen=arr.getJSONObject(0).optJSONObject("team");
        for(int i=0;i<arr.length();i++){
            JSONObject candidate=arr.getJSONObject(i).optJSONObject("team");
            if(candidate!=null && name.trim().equalsIgnoreCase(candidate.optString("name"))){
                chosen=candidate; break;
            }
        }
        if(chosen==null) throw new Exception("Не удалось прочитать команду: "+name);
        return chosen;
    }
    private JSONArray lastFixtures(int teamId) throws Exception {
        // API-Football status=FT restricts results to completed fixtures.
        JSONObject j=request("fixtures?team="+teamId+"&last=5&status=FT");
        JSONArray a=j.optJSONArray("response");
        return a==null?new JSONArray():a;
    }
    private static class TeamStats {
        double gf=0,ga=0,corners=0,yellow=0,fouls=0;
        int games=0, cornerGames=0, cardGames=0, foulGames=0;
    }
    private TeamStats collect(int teamId,JSONArray fixtures) throws Exception {
        TeamStats s=new TeamStats();
        for(int i=0;i<fixtures.length();i++){
            JSONObject f=fixtures.getJSONObject(i);
            JSONObject teams=f.optJSONObject("teams"), goals=f.optJSONObject("goals");
            if(teams==null||goals==null) continue;
            JSONObject homeObj=teams.optJSONObject("home"), awayObj=teams.optJSONObject("away");
            if(homeObj==null||awayObj==null) continue;
            boolean home=homeObj.optInt("id")==teamId;
            int own=home?goals.optInt("home",-1):goals.optInt("away",-1);
            int against=home?goals.optInt("away",-1):goals.optInt("home",-1);
            if(own>=0&&against>=0){s.gf+=own;s.ga+=against;s.games++;}
            JSONObject fixture=f.optJSONObject("fixture");
            if(fixture==null) continue;
            int fixtureId=fixture.optInt("id");
            if(fixtureId<=0) continue;
            JSONObject stat=request("fixtures/statistics?fixture="+fixtureId+"&team="+teamId);
            JSONArray rows=stat.optJSONArray("response");
            if(rows!=null&&rows.length()>0){
                JSONArray stats=rows.getJSONObject(0).optJSONArray("statistics");
                double cor=value(stats,"Corner Kicks"), yell=value(stats,"Yellow Cards"), fou=value(stats,"Fouls");
                if(cor>=0){s.corners+=cor;s.cornerGames++;}
                if(yell>=0){s.yellow+=yell;s.cardGames++;}
                if(fou>=0){s.fouls+=fou;s.foulGames++;}
            }
        }
        return s;
    }
    private double value(JSONArray arr,String type){
        if(arr==null)return -1;
        for(int i=0;i<arr.length();i++){
            JSONObject x=arr.optJSONObject(i);
            if(x!=null&&type.equalsIgnoreCase(x.optString("type"))){
                Object v=x.opt("value"); if(v==null||v==JSONObject.NULL)return -1;
                try{return Double.parseDouble(v.toString().replace("%",""));}catch(Exception e){return -1;}
            }
        }
        return -1;
    }
    private String avg(double sum,int n){return n==0?"нет данных":String.format(Locale.US,"%.2f",sum/n);}
    private String makeReport(JSONObject a,JSONObject b,TeamStats x,TeamStats y,int h2h,int na,int nb){
        double goals=(x.games==0?0:x.gf/(double)x.games)+(y.games==0?0:y.gf/(double)y.games);
        StringBuilder s=new StringBuilder();
        s.append("⚽ АНАЛИЗ МАТЧА\n").append(a.optString("name")).append(" — ").append(b.optString("name")).append("\n\n");
        s.append("ГОЛЫ — последние матчи\n");
        s.append(a.optString("name")).append(": забивает ").append(avg(x.gf,x.games)).append(", пропускает ").append(avg(x.ga,x.games)).append("\n");
        s.append(b.optString("name")).append(": забивает ").append(avg(y.gf,y.games)).append(", пропускает ").append(avg(y.ga,y.games)).append("\n");
        s.append("Расчётный суммарный голевой ориентир: ").append(String.format(Locale.US,"%.2f",goals)).append(" гола за матч.\n\n");
        s.append("УГЛОВЫЕ (среднее за матч)\n");
        s.append(a.optString("name")).append(": ").append(avg(x.corners,x.cornerGames)).append("\n");
        s.append(b.optString("name")).append(": ").append(avg(y.corners,y.cornerGames)).append("\n");
        if(x.cornerGames+y.cornerGames>0)s.append("Общий ориентир: ").append(avg(x.corners+y.corners,x.cornerGames+y.cornerGames)).append(" на команду.\n");
        s.append("\nЖЁЛТЫЕ КАРТОЧКИ\n").append(a.optString("name")).append(": ").append(avg(x.yellow,x.cardGames)).append("\n");
        s.append(b.optString("name")).append(": ").append(avg(y.yellow,y.cardGames)).append("\n\n");
        s.append("ФОЛЫ\n").append(a.optString("name")).append(": ").append(avg(x.fouls,x.foulGames)).append("\n");
        s.append(b.optString("name")).append(": ").append(avg(y.fouls,y.foulGames)).append("\n\n");
        s.append("H2H: найдено последних очных матчей — ").append(h2h).append(".\n");
        s.append("Выборка: ").append(x.games).append(" матчей для первой команды и ").append(y.games).append(" для второй.\n\n");
        s.append("Важно: это средние значения прошлых матчей, а не гарантия исхода или готовая вероятность ставки. Если по угловым/карточкам написано «нет данных», API не предоставил статистику для этих матчей.");
        return s.toString();
    }
    @Override protected void onDestroy(){super.onDestroy();executor.shutdownNow();}
}
