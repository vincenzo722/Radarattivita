package it.violante.radarattivita;

import android.Manifest;
import android.app.*;
import android.os.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.hardware.*;
import android.location.*;
import android.net.Uri;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class MainActivity extends Activity implements SensorEventListener {
    RadarView radar;
    TextView status;
    Spinner category, radius;
    EditText companyName;
    LocationManager lm;
    Location here;
    SensorManager sm;
    Sensor rot;
    float heading = 0;

    final String[] cats = {"Potenziali clienti","Ristoranti","Bar","Hotel","Supermercati","Distributori","Farmacie","Officine","Aziende"};
    final String[] radii = {"500 m","1 km","2 km","5 km","10 km"};
    final String[] overpassServers = {
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://lz4.overpass-api.de/api/interpreter"
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        build();
        lm = (LocationManager)getSystemService(LOCATION_SERVICE);
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, 7);
        } else locate();
        sm = (SensorManager)getSystemService(SENSOR_SERVICE);
        rot = sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (rot != null) sm.registerListener(this, rot, SensorManager.SENSOR_DELAY_UI);
    }

    TextView label(String text) {
        TextView v = new TextView(this);
        v.setText(text);
        v.setTextColor(Color.WHITE);
        v.setTextSize(13);
        v.setPadding(4, 6, 4, 4);
        return v;
    }

    GradientDrawable whiteBox() {
        GradientDrawable d = new GradientDrawable();
        d.setColor(Color.WHITE);
        d.setCornerRadius(14);
        d.setStroke(2, Color.rgb(0,130,90));
        return d;
    }

    ArrayAdapter<String> visibleAdapter(String[] values) {
        return new ArrayAdapter<String>(this, android.R.layout.simple_spinner_dropdown_item, values) {
            @Override public View getView(int position, View convertView, android.view.ViewGroup parent) {
                TextView v = (TextView)super.getView(position, convertView, parent);
                v.setTextColor(Color.BLACK);
                v.setTextSize(16);
                v.setPadding(18, 14, 18, 14);
                v.setBackground(whiteBox());
                return v;
            }
            @Override public View getDropDownView(int position, View convertView, android.view.ViewGroup parent) {
                TextView v = (TextView)super.getDropDownView(position, convertView, parent);
                v.setTextColor(Color.BLACK);
                v.setTextSize(16);
                v.setPadding(22, 18, 22, 18);
                v.setBackgroundColor(Color.WHITE);
                return v;
            }
        };
    }

    void build() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20, 20, 20, 14);
        root.setBackgroundColor(Color.rgb(7,29,25));

        TextView title = new TextView(this);
        title.setText("RADAR ATTIVITÀ");
        title.setTextColor(Color.WHITE);
        title.setTextSize(25);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null, 1);
        root.addView(title, new LinearLayout.LayoutParams(-1,-2));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, 8, 0, 8);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(label("CATEGORIA"));
        category = new Spinner(this);
        category.setAdapter(visibleAdapter(cats));
        left.addView(category, new LinearLayout.LayoutParams(-1,-2));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(12,0,0,0);
        right.addView(label("DISTANZA"));
        radius = new Spinner(this);
        radius.setAdapter(visibleAdapter(radii));
        radius.setSelection(2);
        right.addView(radius, new LinearLayout.LayoutParams(-1,-2));

        row.addView(left, new LinearLayout.LayoutParams(0,-2,2));
        row.addView(right, new LinearLayout.LayoutParams(0,-2,1));
        root.addView(row);

        root.addView(label("AZIENDA SPECIFICA"));
        companyName = new EditText(this);
        companyName.setHint("Scrivi il nome, es. Conad, Enel, Eni...");
        companyName.setHintTextColor(Color.DKGRAY);
        companyName.setTextColor(Color.BLACK);
        companyName.setSingleLine(true);
        companyName.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        companyName.setPadding(18, 12, 18, 12);
        companyName.setBackground(whiteBox());
        companyName.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { scanSpecific(); return true; }
            return false;
        });
        root.addView(companyName, new LinearLayout.LayoutParams(-1,-2));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0,8,0,4);
        Button scan = new Button(this);
        scan.setText("CERCA ATTIVITÀ VICINE");
        scan.setOnClickListener(v -> scanCategory());
        Button specific = new Button(this);
        specific.setText("CERCA AZIENDA");
        specific.setOnClickListener(v -> scanSpecific());
        buttons.addView(scan, new LinearLayout.LayoutParams(0,-2,1));
        buttons.addView(specific, new LinearLayout.LayoutParams(0,-2,1));
        root.addView(buttons);

        radar = new RadarView(this);
        root.addView(radar, new LinearLayout.LayoutParams(-1,0,1));

        status = new TextView(this);
        status.setText("Attendo posizione GPS…");
        status.setTextColor(Color.WHITE);
        status.setTextSize(14);
        status.setGravity(Gravity.CENTER);
        status.setPadding(4,8,4,4);
        root.addView(status);
        setContentView(root);
    }

    void locate() {
        try {
            LocationListener listener = l -> {
                here = l;
                radar.here = l;
                status.setText(String.format(Locale.ITALY,"GPS %.5f, %.5f",l.getLatitude(),l.getLongitude()));
                radar.invalidate();
            };
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER,1500,3,listener);
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,2500,10,listener);
            Location x = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (x == null) x = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            if (x != null) { here = x; radar.here = x; }
        } catch(Exception e) {
            status.setText("Attiva il GPS e consenti la posizione.");
        }
    }

    @Override public void onRequestPermissionsResult(int r, String[] p, int[] g) {
        super.onRequestPermissionsResult(r,p,g);
        if (r==7 && g.length>0 && g[0]==PackageManager.PERMISSION_GRANTED) locate();
    }

    int meters() { return new int[]{500,1000,2000,5000,10000}[radius.getSelectedItemPosition()]; }

    String categoryFilter(int pos) {
        switch(pos) {
            case 0: return "[\"name\"][\"shop\"]";
            case 1: return "[\"amenity\"~\"restaurant|fast_food\"]";
            case 2: return "[\"amenity\"~\"cafe|bar\"]";
            case 3: return "[\"tourism\"~\"hotel|guest_house|motel\"]";
            case 4: return "[\"shop\"=\"supermarket\"]";
            case 5: return "[\"amenity\"=\"fuel\"]";
            case 6: return "[\"amenity\"=\"pharmacy\"]";
            case 7: return "[\"shop\"=\"car_repair\"]";
            default: return "[\"name\"][\"office\"]";
        }
    }

    void scanCategory() {
        if (here == null) { status.setText("Sto cercando la posizione GPS…"); return; }
        int pos = category.getSelectedItemPosition();
        runSearch(categoryFilter(pos), cats[pos]);
    }

    String regexEscape(String s) {
        return s.replace("\\","\\\\").replace("\"","\\\"")
                .replace(".","\\.").replace("[","\\[").replace("]","\\]")
                .replace("(","\\(").replace(")","\\)").replace("?","\\?")
                .replace("+","\\+").replace("*","\\*");
    }

    void scanSpecific() {
        String name = companyName.getText().toString().trim();
        if (name.length() < 2) {
            status.setText("Scrivi il nome dell'azienda da cercare.");
            companyName.requestFocus();
            return;
        }
        if (here == null) { status.setText("Sto cercando la posizione GPS…"); return; }
        String filter = "[\"name\"~\"" + regexEscape(name) + "\",i]";
        runSearch(filter, "Azienda: " + name);
    }

    String makeQuery(String filter) {
        double la = here.getLatitude(), lo = here.getLongitude();
        int r = meters();
        return "[out:json][timeout:20];(" +
                "node"+filter+"(around:"+r+","+la+","+lo+");" +
                "way"+filter+"(around:"+r+","+la+","+lo+");" +
                ");out center tags;";
    }

    String callOverpass(String server, String query) throws Exception {
        byte[] body = ("data=" + URLEncoder.encode(query, "UTF-8")).getBytes(StandardCharsets.UTF_8);
        HttpURLConnection c = (HttpURLConnection)new URL(server).openConnection();
        c.setRequestMethod("POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        c.setRequestProperty("User-Agent", "RadarAttivita/1.3 Android");
        c.setRequestProperty("Accept", "application/json");
        c.setConnectTimeout(12000);
        c.setReadTimeout(25000);
        c.setFixedLengthStreamingMode(body.length);
        try (OutputStream os = c.getOutputStream()) { os.write(body); }
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) throw new IOException("HTTP " + code);
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            c.disconnect();
        }
    }

    void runSearch(String filter, String label) {
        status.setText("Ricerca " + label + "…");
        new Thread(() -> {
            String lastError = "";
            String query = makeQuery(filter);
            for (int i=0; i<overpassServers.length; i++) {
                try {
                    final int serverNo = i + 1;
                    runOnUiThread(() -> status.setText("Ricerca " + label + "… server " + serverNo));
                    String json = callOverpass(overpassServers[i], query);
                    ArrayList<Place> ps = parse(json);
                    runOnUiThread(() -> {
                        radar.places = ps;
                        radar.max = meters();
                        radar.invalidate();
                        if (ps.isEmpty()) status.setText("Nessun risultato nel raggio selezionato.");
                        else status.setText(ps.size() + " risultati • tocca un punto sul radar");
                    });
                    return;
                } catch(Exception e) {
                    lastError = e.getClass().getSimpleName() + ": " + (e.getMessage()==null ? "errore" : e.getMessage());
                }
            }
            final String err = lastError;
            runOnUiThread(() -> status.setText("Connessione ai dati attività non riuscita. " + err));
        }).start();
    }

    ArrayList<Place> parse(String s) throws Exception {
        ArrayList<Place> a = new ArrayList<>();
        JSONArray elements = new JSONObject(s).optJSONArray("elements");
        if (elements == null) return a;
        for (int i=0; i<elements.length() && a.size()<100; i++) {
            JSONObject e = elements.getJSONObject(i);
            double la = e.optDouble("lat", Double.NaN), lo = e.optDouble("lon", Double.NaN);
            if ((Double.isNaN(la) || Double.isNaN(lo)) && e.has("center")) {
                JSONObject center = e.getJSONObject("center");
                la = center.optDouble("lat", Double.NaN);
                lo = center.optDouble("lon", Double.NaN);
            }
            if (Double.isNaN(la) || Double.isNaN(lo)) continue;
            JSONObject tags = e.optJSONObject("tags");
            String n = "Attività";
            if (tags != null) n = tags.optString("name", tags.optString("brand", tags.optString("operator","Attività")));
            a.add(new Place(n,la,lo));
        }
        Collections.sort(a, (x,y) -> Float.compare(distanceTo(x), distanceTo(y)));
        return a;
    }

    float distanceTo(Place x) {
        if (here == null) return Float.MAX_VALUE;
        float[] r = new float[1];
        Location.distanceBetween(here.getLatitude(),here.getLongitude(),x.la,x.lo,r);
        return r[0];
    }

    @Override public void onSensorChanged(SensorEvent e) {
        float[] rm = new float[9], o = new float[3];
        SensorManager.getRotationMatrixFromVector(rm,e.values);
        SensorManager.getOrientation(rm,o);
        heading = (float)Math.toDegrees(o[0]);
        if (radar != null) radar.invalidate();
    }
    @Override public void onAccuracyChanged(Sensor s,int a) {}
    @Override protected void onResume() {
        super.onResume();
        if (sm!=null && rot!=null) sm.registerListener(this,rot,SensorManager.SENSOR_DELAY_UI);
    }
    @Override protected void onPause() {
        super.onPause();
        if (sm!=null) sm.unregisterListener(this);
    }

    class Place {
        String n; double la,lo;
        Place(String n,double a,double o){this.n=n;la=a;lo=o;}
    }

    class RadarView extends View {
        Paint p = new Paint(1);
        ArrayList<Place> places = new ArrayList<>();
        Location here;
        int max = 2000;
        ArrayList<Dot> dots = new ArrayList<>();
        class Dot { float x,y; Place place; Dot(float x,float y,Place p){this.x=x;this.y=y;this.place=p;} }

        RadarView(Context c) {
            super(c);
            setOnTouchListener((v,e) -> {
                if (e.getAction()==MotionEvent.ACTION_UP) {
                    for (Dot d:dots) {
                        if (Math.hypot(e.getX()-d.x,e.getY()-d.y)<38) {
                            Place x=d.place;
                            new AlertDialog.Builder(MainActivity.this)
                                .setTitle(x.n)
                                .setMessage(String.format(Locale.ITALY,"Distanza: %.0f m",distanceTo(x)))
                                .setPositiveButton("NAVIGA",(q,w)->startActivity(new Intent(Intent.ACTION_VIEW,
                                    Uri.parse("geo:"+x.la+","+x.lo+"?q="+x.la+","+x.lo+"("+Uri.encode(x.n)+")"))))
                                .setNegativeButton("Chiudi",null).show();
                            return true;
                        }
                    }
                }
                return true;
            });
        }

        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            float cx=getWidth()/2f, cy=getHeight()/2f, R=Math.min(getWidth(),getHeight())*.43f;
            p.setStyle(Paint.Style.FILL); p.setColor(Color.rgb(4,45,36)); c.drawCircle(cx,cy,R,p);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(Color.rgb(20,150,110));
            for(int i=1;i<=4;i++) c.drawCircle(cx,cy,R*i/4,p);
            c.drawLine(cx-R,cy,cx+R,cy,p);
            c.drawLine(cx,cy-R,cx,cy+R,p);
            p.setStyle(Paint.Style.FILL); p.setColor(Color.WHITE); p.setTextSize(21);
            c.drawText("DAVANTI",cx-38,cy-R+24,p);
            p.setColor(Color.YELLOW); c.drawCircle(cx,cy,10,p);
            dots.clear();
            if (here==null) return;
            for (Place x:places) {
                float[] res=new float[2];
                Location.distanceBetween(here.getLatitude(),here.getLongitude(),x.la,x.lo,res);
                if (res[0]>max) continue;
                double br=Math.toRadians(res[1]-heading);
                float rr=R*res[0]/max;
                float dx=cx+(float)Math.sin(br)*rr, dy=cy-(float)Math.cos(br)*rr;
                p.setColor(Color.rgb(0,255,160)); c.drawCircle(dx,dy,10,p);
                dots.add(new Dot(dx,dy,x));
            }
        }
    }
}
