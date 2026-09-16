package it.violante.radarattivita;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.location.*;
import android.net.Uri;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.*;

public class MainActivity extends Activity {
    RadarView radar; TextView status; Spinner category, radius; LocationManager lm; Location here;
    final String[] cats={"Ristoranti","Bar","Hotel","Supermercati","Distributori","Farmacie","Officine","Aziende"};
    final String[] tags={"amenity~\"restaurant|fast_food\"","amenity=\"cafe\"","tourism=\"hotel\"","shop=\"supermarket\"","amenity=\"fuel\"","amenity=\"pharmacy\"","shop=\"car_repair\"","office"};
    @Override public void onCreate(Bundle b){super.onCreate(b); build(); lm=(LocationManager)getSystemService(LOCATION_SERVICE); if(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION},7); else locate();}
    void build(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(24,24,24,16); root.setBackgroundColor(Color.rgb(7,29,25));
        TextView title=new TextView(this); title.setText("RADAR ATTIVITÀ"); title.setTextColor(Color.WHITE); title.setTextSize(25); title.setGravity(Gravity.CENTER); title.setTypeface(null,1); root.addView(title,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        category=new Spinner(this); category.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,cats));
        radius=new Spinner(this); radius.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"500 m","1 km","2 km","5 km","10 km"}));
        row.addView(category,new LinearLayout.LayoutParams(0,-2,1)); row.addView(radius,new LinearLayout.LayoutParams(0,-2,1)); root.addView(row);
        radar=new RadarView(this); root.addView(radar,new LinearLayout.LayoutParams(-1,0,1));
        Button scan=new Button(this); scan.setText("CERCA ATTIVITÀ VICINE"); scan.setOnClickListener(v->scan()); root.addView(scan);
        status=new TextView(this); status.setText("Attendo posizione GPS…"); status.setTextColor(Color.WHITE); status.setTextSize(15); status.setGravity(Gravity.CENTER); root.addView(status);
        setContentView(root);
    }
    void locate(){ try { lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,1500,3,l->{here=l; status.setText(String.format(Locale.ITALY,"GPS %.5f, %.5f",l.getLatitude(),l.getLongitude())); radar.here=l; radar.invalidate();}); Location x=lm.getLastKnownLocation(LocationManager.GPS_PROVIDER); if(x!=null){here=x;radar.here=x;} }catch(Exception e){status.setText("Attiva il GPS.");}}
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){super.onRequestPermissionsResult(r,p,g); if(r==7&&g.length>0&&g[0]==PackageManager.PERMISSION_GRANTED)locate();}
    int meters(){return new int[]{500,1000,2000,5000,10000}[radius.getSelectedItemPosition()];}
    void scan(){ if(here==null){status.setText("Sto cercando la posizione GPS…");return;} status.setText("Ricerca in corso…"); new Thread(()->{
        try{
            String t=tags[category.getSelectedItemPosition()], filter=t.equals("office")?"[\"office\"]":"["+t+"]";
            String q="[out:json];(node"+filter+"(around:"+meters()+","+here.getLatitude()+","+here.getLongitude()+");way"+filter+"(around:"+meters()+","+here.getLatitude()+","+here.getLongitude()+"););out center tags;";
            URL u=new URL("https://overpass-api.de/api/interpreter?data="+URLEncoder.encode(q,"UTF-8"));
            HttpURLConnection c=(HttpURLConnection)u.openConnection(); c.setRequestProperty("User-Agent","RadarAttivita/1.0"); c.setConnectTimeout(15000); c.setReadTimeout(25000);
            String json=new String(c.getInputStream().readAllBytes(),StandardCharsets.UTF_8); ArrayList<Place> ps=parse(json); runOnUiThread(()->{radar.places=ps;radar.max=meters();radar.invalidate();status.setText(ps.size()+" attività trovate • tocca un punto");});
        }catch(Exception e){runOnUiThread(()->status.setText("Ricerca non disponibile. Riprova."));}
    }).start();}
    ArrayList<Place> parse(String s){ArrayList<Place>a=new ArrayList<>(); Pattern p=Pattern.compile("\\{[^{}]*?\"lat\"\\s*:\\s*([0-9.-]+)[^{}]*?\"lon\"\\s*:\\s*([0-9.-]+)(.*?)\\}",Pattern.DOTALL); Matcher m=p.matcher(s); while(m.find()&&a.size()<80){double la=Double.parseDouble(m.group(1)),lo=Double.parseDouble(m.group(2));String z=m.group(3),n="Attività";Matcher nm=Pattern.compile("\"name\"\\s*:\\s*\"([^\"]+)\"").matcher(z);if(nm.find())n=nm.group(1);a.add(new Place(n,la,lo));} return a;}
    class Place{String n;double la,lo;Place(String n,double a,double o){this.n=n;la=a;lo=o;}}
    class RadarView extends View{
        Paint p=new Paint(1); ArrayList<Place>places=new ArrayList<>(); Location here; int max=2000; ArrayList<float[]> dots=new ArrayList<>();
        RadarView(Context c){super(c);setOnTouchListener((v,e)->{if(e.getAction()==1){for(int i=0;i<dots.size();i++){float[]d=dots.get(i);if(Math.hypot(e.getX()-d[0],e.getY()-d[1])<35){Place x=places.get(i);new AlertDialog.Builder(MainActivity.this).setTitle(x.n).setMessage(String.format(Locale.ITALY,"Distanza: %.0f m",dist(x))).setPositiveButton("NAVIGA",(q,w)->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("geo:"+x.la+","+x.lo+"?q="+x.la+","+x.lo+"("+Uri.encode(x.n)+")")))).setNegativeButton("Chiudi",null).show();return true;}}}return true;});}
        float dist(Place x){float[]r=new float[1];Location.distanceBetween(here.getLatitude(),here.getLongitude(),x.la,x.lo,r);return r[0];}
        protected void onDraw(Canvas c){super.onDraw(c);float cx=getWidth()/2f,cy=getHeight()/2f,R=Math.min(getWidth(),getHeight())*.43f;p.setStyle(Paint.Style.FILL);p.setColor(Color.rgb(4,45,36));c.drawCircle(cx,cy,R,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(Color.rgb(20,150,110));for(int i=1;i<=4;i++)c.drawCircle(cx,cy,R*i/4,p);c.drawLine(cx-R,cy,cx+R,cy,p);c.drawLine(cx,cy-R,cx,cy+R,p);p.setStyle(Paint.Style.FILL);p.setColor(Color.WHITE);p.setTextSize(24);c.drawText("N",cx-8,cy-R+25,p);p.setColor(Color.YELLOW);c.drawCircle(cx,cy,10,p);dots.clear();if(here==null)return;for(Place x:places){float[]res=new float[2];Location.distanceBetween(here.getLatitude(),here.getLongitude(),x.la,x.lo,res);if(res[0]>max)continue;double br=Math.toRadians(res[1]);float rr=R*res[0]/max;float dx=cx+(float)Math.sin(br)*rr,dy=cy-(float)Math.cos(br)*rr;p.setColor(Color.rgb(0,255,160));c.drawCircle(dx,dy,9,p);dots.add(new float[]{dx,dy});}}
    }
}
