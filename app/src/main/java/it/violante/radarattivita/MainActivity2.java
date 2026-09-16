package it.violante.radarattivita;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.location.Location;
import android.net.Uri;
import android.text.InputType;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import java.text.DateFormat;
import java.util.*;

public class MainActivity2 extends MainActivity {
    VisitStore store;

    @Override void build() {
        store = new VisitStore(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(20,20,20,14);
        root.setBackgroundColor(Color.rgb(7,29,25));

        TextView title = new TextView(this);
        title.setText("RADAR ATTIVITÀ");
        title.setTextColor(Color.WHITE);
        title.setTextSize(25);
        title.setGravity(Gravity.CENTER);
        title.setTypeface(null,1);
        root.addView(title,new LinearLayout.LayoutParams(-1,-2));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0,8,0,8);

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        left.addView(label("CATEGORIA"));
        category = new Spinner(this);
        category.setAdapter(visibleAdapter(cats));
        left.addView(category,new LinearLayout.LayoutParams(-1,-2));

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setPadding(12,0,0,0);
        right.addView(label("DISTANZA"));
        radius = new Spinner(this);
        radius.setAdapter(visibleAdapter(radii));
        radius.setSelection(2);
        right.addView(radius,new LinearLayout.LayoutParams(-1,-2));

        row.addView(left,new LinearLayout.LayoutParams(0,-2,2));
        row.addView(right,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(row);

        root.addView(label("AZIENDA SPECIFICA"));
        companyName = new EditText(this);
        companyName.setHint("Scrivi il nome, es. Conad, Enel, Eni...");
        companyName.setHintTextColor(Color.DKGRAY);
        companyName.setTextColor(Color.BLACK);
        companyName.setSingleLine(true);
        companyName.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        companyName.setPadding(18,12,18,12);
        companyName.setBackground(whiteBox());
        companyName.setOnEditorActionListener((v,actionId,event)->{
            if(actionId==EditorInfo.IME_ACTION_SEARCH){ scanSpecific(); return true; }
            return false;
        });
        root.addView(companyName,new LinearLayout.LayoutParams(-1,-2));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0,8,0,4);

        Button scan = new Button(this);
        scan.setText("CERCA ATTIVITÀ VICINE");
        scan.setOnClickListener(v->scanCategory());

        Button specific = new Button(this);
        specific.setText("CERCA AZIENDA");
        specific.setOnClickListener(v->scanSpecific());

        buttons.addView(scan,new LinearLayout.LayoutParams(0,-2,1));
        buttons.addView(specific,new LinearLayout.LayoutParams(0,-2,1));
        root.addView(buttons);

        radar = new RadarView2(this);
        root.addView(radar,new LinearLayout.LayoutParams(-1,0,1));

        LinearLayout legend = new LinearLayout(this);
        legend.setOrientation(LinearLayout.HORIZONTAL);
        legend.setGravity(Gravity.CENTER);
        legend.addView(legendItem("● Da visitare",Color.rgb(0,255,160)));
        legend.addView(legendItem("● Visitata",Color.YELLOW));
        legend.addView(legendItem("● Visitata + note",Color.rgb(255,140,0)));
        root.addView(legend);

        Button archive = new Button(this);
        archive.setText("ARCHIVIO VISITE E COMMENTI");
        archive.setOnClickListener(v->showArchive());
        root.addView(archive);

        status = new TextView(this);
        status.setText("Attendo posizione GPS…");
        status.setTextColor(Color.WHITE);
        status.setTextSize(14);
        status.setGravity(Gravity.CENTER);
        status.setPadding(4,8,4,4);
        root.addView(status);

        setContentView(root);
    }

    TextView legendItem(String text,int color){
        TextView v=new TextView(this);
        v.setText(text);
        v.setTextColor(color);
        v.setTextSize(11);
        v.setPadding(7,4,7,4);
        return v;
    }

    String placeKey(Place p){
        return p.n+"@"+String.format(Locale.US,"%.5f,%.5f",p.la,p.lo);
    }

    void showSheet(Place p){
        String key=placeKey(p);
        VisitStore.Item saved=store.get(key);

        ScrollView sv=new ScrollView(this);
        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(28,18,28,10);
        sv.addView(box);

        TextView info=new TextView(this);
        info.setText("Categoria: "+cats[category.getSelectedItemPosition()]+"\nDistanza: "+
                String.format(Locale.ITALY,"%.0f m",distanceTo(p))+
                "\nCoordinate: "+String.format(Locale.ITALY,"%.5f, %.5f",p.la,p.lo));
        info.setTextSize(15);
        box.addView(info);

        CheckBox visited=new CheckBox(this);
        visited.setText("ATTIVITÀ VISITATA");
        visited.setChecked(saved.visited);
        box.addView(visited);

        TextView l=new TextView(this);
        l.setText("Commenti / esito visita");
        l.setPadding(0,12,0,4);
        box.addView(l);

        EditText comment=new EditText(this);
        comment.setMinLines(4);
        comment.setGravity(Gravity.TOP);
        comment.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        comment.setText(saved.comment);
        comment.setHint("Es. parlato con il titolare, richiamare venerdì, interessato all'offerta...");
        box.addView(comment,new LinearLayout.LayoutParams(-1,-2));

        AlertDialog dlg=new AlertDialog.Builder(this)
                .setTitle(p.n)
                .setView(sv)
                .setPositiveButton("SALVA",null)
                .setNeutralButton("NAVIGA",null)
                .setNegativeButton("CHIUDI",null)
                .create();

        dlg.setOnShowListener(v->{
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(q->{
                store.save(key,p.n,visited.isChecked(),comment.getText().toString().trim());
                radar.invalidate();
                status.setText("Scheda salvata: "+p.n);
                dlg.dismiss();
            });
            dlg.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(q->{
                try{
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(
                            "geo:"+p.la+","+p.lo+"?q="+p.la+","+p.lo+"("+Uri.encode(p.n)+")")));
                }catch(Exception ignored){}
            });
        });
        dlg.show();
    }

    void showArchive(){
        ArrayList<VisitStore.Item> rows=store.all();
        if(rows.isEmpty()){
            new AlertDialog.Builder(this).setTitle("Archivio visite")
                    .setMessage("Non hai ancora salvato visite o commenti.")
                    .setPositiveButton("OK",null).show();
            return;
        }

        ScrollView sv=new ScrollView(this);
        LinearLayout list=new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(20,10,20,10);
        sv.addView(list);

        for(VisitStore.Item r:rows){
            TextView v=new TextView(this);
            String state=r.visited?"VISITATA":"DA VISITARE";
            v.setText(r.name+"\n"+state+(r.comment.isEmpty()?"":"\n"+r.comment)+
                    "\nAggiornata: "+DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT)
                    .format(new Date(r.updated)));
            v.setTextColor(Color.BLACK);
            v.setTextSize(15);
            v.setPadding(16,14,16,14);
            v.setBackgroundColor(Color.WHITE);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
            lp.setMargins(0,0,0,10);
            list.addView(v,lp);
        }

        new AlertDialog.Builder(this)
                .setTitle("Archivio visite e commenti ("+rows.size()+")")
                .setView(sv)
                .setPositiveButton("CHIUDI",null)
                .show();
    }

    class RadarView2 extends RadarView {
        RadarView2(Context c){
            super(c);
            setOnTouchListener((v,e)->{
                if(e.getAction()==MotionEvent.ACTION_UP){
                    Dot2 best=null;
                    double bd=9999;
                    for(Dot2 d:dots2){
                        double dd=Math.hypot(e.getX()-d.x,e.getY()-d.y);
                        if(dd<bd){bd=dd;best=d;}
                    }
                    if(best!=null && bd<45){
                        showSheet(best.place);
                        return true;
                    }
                }
                return true;
            });
        }

        ArrayList<Dot2> dots2=new ArrayList<>();
        class Dot2{
            float x,y;
            Place place;
            Dot2(float x,float y,Place p){this.x=x;this.y=y;this.place=p;}
        }

        @Override protected void onDraw(Canvas c){
            float cx=getWidth()/2f,cy=getHeight()/2f,R=Math.min(getWidth(),getHeight())*.43f;

            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(4,45,36));
            c.drawCircle(cx,cy,R,p);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2);
            p.setColor(Color.rgb(20,150,110));
            for(int i=1;i<=4;i++)c.drawCircle(cx,cy,R*i/4,p);
            c.drawLine(cx-R,cy,cx+R,cy,p);
            c.drawLine(cx,cy-R,cx,cy+R,p);

            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.WHITE);
            p.setTextSize(21);
            c.drawText("DAVANTI",cx-38,cy-R+24,p);

            p.setColor(Color.YELLOW);
            c.drawCircle(cx,cy,10,p);

            dots2.clear();
            if(here==null)return;

            for(Place x:places){
                float[] res=new float[2];
                Location.distanceBetween(here.getLatitude(),here.getLongitude(),x.la,x.lo,res);
                if(res[0]>max)continue;

                double br=Math.toRadians(res[1]-heading);
                float rr=R*res[0]/max;
                float dx=cx+(float)Math.sin(br)*rr;
                float dy=cy-(float)Math.cos(br)*rr;

                VisitStore.Item rec=store.get(placeKey(x));
                int color=Color.rgb(0,255,160);
                if(rec.visited && !rec.comment.trim().isEmpty()) color=Color.rgb(255,140,0);
                else if(rec.visited) color=Color.YELLOW;

                p.setColor(color);
                c.drawCircle(dx,dy,11,p);
                dots2.add(new Dot2(dx,dy,x));
            }
        }
    }
}
