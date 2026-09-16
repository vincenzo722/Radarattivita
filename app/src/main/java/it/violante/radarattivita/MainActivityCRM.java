package it.violante.radarattivita;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.*;
import android.location.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.speech.RecognizerIntent;
import android.text.*;
import android.text.method.ScrollingMovementMethod;
import android.view.*;
import android.view.inputmethod.EditorInfo;
import android.widget.*;
import org.json.*;
import java.io.*;
import java.text.*;
import java.util.*;

public class MainActivityCRM extends MainActivity {
    CrmStore store;
    Spinner crmCategory, filterStatus;
    EditText zoneEdit;
    Location radarCenter;
    final String[] crmCats={"Potenziali clienti","Ristoranti","Bar","Hotel","Supermercati","Distributori","Farmacie","Officine","Aziende","Mie aziende"};
    final String[] filterLabels={"Tutte","Da visitare","Visitate","Interessati","Da richiamare","Clienti","Non interessati"};
    final String[] filterCodes={"","DA_VISITARE","VISITATA","INTERESSATO","DA_RICHIAMARE","CLIENTE","NON_INTERESSATO"};
    EditText currentCommentEdit;
    Place currentPlace;
    String currentRecordKey="";
    byte[] pendingExport;
    String pendingMime="application/octet-stream", pendingName="export.dat";
    HashSet<String> nearbyShown=new HashSet<>();
    Handler nearbyHandler=new Handler(Looper.getMainLooper());
    Runnable nearbyTask=new Runnable(){public void run(){checkNearby();nearbyHandler.postDelayed(this,20000);}};

    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},91);
        nearbyHandler.postDelayed(nearbyTask,8000);
    }

    @Override void build(){
        store=new CrmStore(this);
        LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(14,12,14,10);root.setBackgroundColor(Color.rgb(7,29,25));
        TextView title=label("RADAR ATTIVITÀ CRM");title.setTextSize(23);title.setGravity(Gravity.CENTER);title.setTypeface(null,1);root.addView(title);

        LinearLayout r1=new LinearLayout(this);r1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout a=new LinearLayout(this);a.setOrientation(LinearLayout.VERTICAL);a.addView(label("CATEGORIA"));crmCategory=new Spinner(this);crmCategory.setAdapter(visibleAdapter(crmCats));a.addView(crmCategory,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout d=new LinearLayout(this);d.setOrientation(LinearLayout.VERTICAL);d.setPadding(10,0,0,0);d.addView(label("DISTANZA"));radius=new Spinner(this);radius.setAdapter(visibleAdapter(radii));radius.setSelection(2);d.addView(radius,new LinearLayout.LayoutParams(-1,-2));
        r1.addView(a,new LinearLayout.LayoutParams(0,-2,2));r1.addView(d,new LinearLayout.LayoutParams(0,-2,1));root.addView(r1);

        LinearLayout searchRow=new LinearLayout(this);searchRow.setOrientation(LinearLayout.HORIZONTAL);
        companyName=new EditText(this);companyName.setHint("Azienda specifica");companyName.setSingleLine(true);companyName.setTextColor(Color.BLACK);companyName.setHintTextColor(Color.DKGRAY);companyName.setBackground(whiteBox());companyName.setImeOptions(EditorInfo.IME_ACTION_SEARCH);searchRow.addView(companyName,new LinearLayout.LayoutParams(0,-2,1));
        Button bSearch=new Button(this);bSearch.setText("CERCA");bSearch.setOnClickListener(v->scanSpecific());searchRow.addView(bSearch);root.addView(searchRow);

        LinearLayout zoneRow=new LinearLayout(this);zoneRow.setOrientation(LinearLayout.HORIZONTAL);zoneEdit=new EditText(this);zoneEdit.setHint("Comune / zona");zoneEdit.setSingleLine(true);zoneEdit.setTextColor(Color.BLACK);zoneEdit.setHintTextColor(Color.DKGRAY);zoneEdit.setBackground(whiteBox());zoneRow.addView(zoneEdit,new LinearLayout.LayoutParams(0,-2,1));Button bz=new Button(this);bz.setText("CERCA ZONA");bz.setOnClickListener(v->searchZone());zoneRow.addView(bz);root.addView(zoneRow);

        LinearLayout act=new LinearLayout(this);act.setOrientation(LinearLayout.HORIZONTAL);Button scan=new Button(this);scan.setText("VICINE");scan.setOnClickListener(v->scanCategory());Button route=new Button(this);route.setText("PERCORSO");route.setOnClickListener(v->openOptimizedRoute());Button imp=new Button(this);imp.setText("IMPORTA");imp.setOnClickListener(v->importCompanies());act.addView(scan,new LinearLayout.LayoutParams(0,-2,1));act.addView(route,new LinearLayout.LayoutParams(0,-2,1));act.addView(imp,new LinearLayout.LayoutParams(0,-2,1));root.addView(act);

        LinearLayout fr=new LinearLayout(this);fr.setOrientation(LinearLayout.HORIZONTAL);TextView fl=label("FILTRO STATO");fr.addView(fl);filterStatus=new Spinner(this);filterStatus.setAdapter(visibleAdapter(filterLabels));filterStatus.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener(){public void onItemSelected(android.widget.AdapterView<?>p,View v,int pos,long id){if(radar!=null)radar.invalidate();}public void onNothingSelected(android.widget.AdapterView<?>p){}});fr.addView(filterStatus,new LinearLayout.LayoutParams(0,-2,1));root.addView(fr);

        radar=new RadarCrm(this);root.addView(radar,new LinearLayout.LayoutParams(-1,0,1));
        TextView legend=label("● verde da visitare   ● giallo visitata   ● arancio interessato   ● magenta da richiamare   ● blu cliente   ● grigio non interessato");legend.setTextSize(10);legend.setGravity(Gravity.CENTER);root.addView(legend);

        LinearLayout tools=new LinearLayout(this);tools.setOrientation(LinearLayout.HORIZONTAL);Button ar=new Button(this);ar.setText("ARCHIVIO");ar.setOnClickListener(v->showArchive());Button st=new Button(this);st.setText("STATISTICHE");st.setOnClickListener(v->showStats());Button ex=new Button(this);ex.setText("ESPORTA");ex.setOnClickListener(v->chooseExport());tools.addView(ar,new LinearLayout.LayoutParams(0,-2,1));tools.addView(st,new LinearLayout.LayoutParams(0,-2,1));tools.addView(ex,new LinearLayout.LayoutParams(0,-2,1));root.addView(tools);

        status=new TextView(this);status.setText("Attendo posizione GPS…");status.setTextColor(Color.WHITE);status.setTextSize(13);status.setGravity(Gravity.CENTER);root.addView(status);
        TextView osm=label("Dati attività: © OpenStreetMap contributors");osm.setTextSize(9);osm.setGravity(Gravity.CENTER);root.addView(osm);
        setContentView(root);
    }

    String currentCategory(){int p=crmCategory.getSelectedItemPosition();return p>=0&&p<crmCats.length?crmCats[p]:"Attività";}
    String placeKey(Place p){return p.n+"@"+String.format(Locale.US,"%.5f,%.5f",p.la,p.lo);}
    String pretty(String s){return s.replace('_',' ').toLowerCase(Locale.ITALY);}

    @Override int meters(){return new int[]{500,1000,2000,5000,10000}[radius.getSelectedItemPosition()];}
    @Override float distanceTo(Place x){Location c=radarCenter!=null?radarCenter:here;if(c==null)return Float.MAX_VALUE;float[]r=new float[1];Location.distanceBetween(c.getLatitude(),c.getLongitude(),x.la,x.lo,r);return r[0];}
    @Override String makeQuery(String filter){Location c=radarCenter!=null?radarCenter:here;double la=c.getLatitude(),lo=c.getLongitude();int r=meters();return "[out:json][timeout:20];(node"+filter+"(around:"+r+","+la+","+lo+");way"+filter+"(around:"+r+","+la+","+lo+"););out center tags;";}

    @Override void scanCategory(){
        int p=crmCategory.getSelectedItemPosition();radarCenter=here;if(radarCenter==null){status.setText("Attendo posizione GPS…");return;}radar.here=radarCenter;
        if(p==9){showCustomCompanies();return;}runSearch(categoryFilter(p),crmCats[p]);
    }
    @Override void scanSpecific(){String name=companyName.getText().toString().trim();if(name.length()<2){status.setText("Scrivi il nome dell'azienda.");return;}if(radarCenter==null)radarCenter=here;if(radarCenter==null){status.setText("Attendo posizione GPS…");return;}radar.here=radarCenter;runSearch("[\"name\"~\""+regexEscape(name)+"\",i]","Azienda: "+name);}

    void searchZone(){String z=zoneEdit.getText().toString().trim();if(z.length()<2)return;status.setText("Cerco la zona "+z+"…");new Thread(()->{try{Geocoder g=new Geocoder(this,Locale.ITALY);List<Address> l=g.getFromLocationName(z,1);if(l==null||l.isEmpty())throw new Exception("zona non trovata");Address ad=l.get(0);Location c=new Location("zona");c.setLatitude(ad.getLatitude());c.setLongitude(ad.getLongitude());runOnUiThread(()->{radarCenter=c;radar.here=c;int p=crmCategory.getSelectedItemPosition();if(p==9)showCustomCompanies();else runSearch(categoryFilter(Math.min(p,8)),crmCats[p]);});}catch(Exception e){runOnUiThread(()->status.setText("Zona non trovata. Riprova con comune e provincia."));}}).start();}

    void showCustomCompanies(){ArrayList<Place> ps=new ArrayList<>();for(CrmStore.Custom x:store.customs()){Place p=new Place(x.name,x.lat,x.lon);if(distanceTo(p)<=meters())ps.add(p);}Collections.sort(ps,(x,y)->Float.compare(distanceTo(x),distanceTo(y)));radar.places=ps;radar.max=meters();radar.invalidate();status.setText(ps.size()+" aziende importate nel raggio");}

    int statusColor(String s){if("VISITATA".equals(s))return Color.YELLOW;if("INTERESSATO".equals(s))return Color.rgb(255,140,0);if("DA_RICHIAMARE".equals(s))return Color.MAGENTA;if("CLIENTE".equals(s))return Color.rgb(50,120,255);if("NON_INTERESSATO".equals(s))return Color.LTGRAY;return Color.rgb(0,255,160);}
    boolean passFilter(String s){int p=filterStatus.getSelectedItemPosition();return p<=0||filterCodes[p].equals(s);}

    void showSheet(Place p){
        currentPlace=p;currentRecordKey=placeKey(p);CrmStore.Item rec=store.get(currentRecordKey);if(rec.name.isEmpty())rec.name=p.n;if(Double.isNaN(rec.lat)){rec.lat=p.la;rec.lon=p.lo;}if(rec.category.isEmpty())rec.category=currentCategory();
        ScrollView sv=new ScrollView(this);LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(24,12,24,10);sv.addView(box);
        TextView info=new TextView(this);info.setText("Distanza: "+String.format(Locale.ITALY,"%.0f m",distanceTo(p))+"\nCoordinate: "+String.format(Locale.ITALY,"%.5f, %.5f",p.la,p.lo));box.addView(info);
        Spinner st=new Spinner(this);st.setAdapter(visibleAdapter(new String[]{"Da visitare","Visitata","Interessato","Da richiamare","Cliente","Non interessato"}));int ix=Arrays.asList(CrmStore.STATUSES).indexOf(rec.status);st.setSelection(Math.max(0,ix));box.addView(st);
        EditText phone=field("Telefono",rec.phone);box.addView(phone);EditText address=field("Indirizzo",rec.address);box.addView(address);EditText web=field("Sito web",rec.website);box.addView(web);
        TextView next=new TextView(this);next.setPadding(0,8,0,8);next.setText(rec.nextContact>0?"Prossimo contatto: "+DateFormat.getDateTimeInstance().format(new Date(rec.nextContact)):"Prossimo contatto: non impostato");box.addView(next);
        final long[] nextTime={rec.nextContact};Button setNext=new Button(this);setNext.setText("IMPOSTA PROSSIMO CONTATTO");setNext.setOnClickListener(v->pickDateTime(nextTime,next));box.addView(setNext);
        currentCommentEdit=new EditText(this);currentCommentEdit.setHint("Commenti / esito visita");currentCommentEdit.setMinLines(4);currentCommentEdit.setGravity(Gravity.TOP);currentCommentEdit.setText(rec.comment);box.addView(currentCommentEdit);
        LinearLayout media=new LinearLayout(this);Button voice=new Button(this);voice.setText("🎤 NOTA VOCALE");voice.setOnClickListener(v->startVoice());Button photo=new Button(this);photo.setText("FOTO");photo.setOnClickListener(v->pickFile(true));Button doc=new Button(this);doc.setText("DOC");doc.setOnClickListener(v->pickFile(false));media.addView(voice,new LinearLayout.LayoutParams(0,-2,1));media.addView(photo,new LinearLayout.LayoutParams(0,-2,1));media.addView(doc,new LinearLayout.LayoutParams(0,-2,1));box.addView(media);
        LinearLayout contact=new LinearLayout(this);Button call=new Button(this);call.setText("CHIAMA");call.setOnClickListener(v->openCall(phone.getText().toString()));Button wa=new Button(this);wa.setText("WHATSAPP");wa.setOnClickListener(v->openWhatsApp(phone.getText().toString(),p.n));Button nav=new Button(this);nav.setText("NAVIGA");nav.setOnClickListener(v->openNav(p));contact.addView(call,new LinearLayout.LayoutParams(0,-2,1));contact.addView(wa,new LinearLayout.LayoutParams(0,-2,1));contact.addView(nav,new LinearLayout.LayoutParams(0,-2,1));box.addView(contact);
        if(!rec.photo.isEmpty()){Button op=new Button(this);op.setText("APRI FOTO");op.setOnClickListener(v->openUri(rec.photo));box.addView(op);}if(!rec.docUri.isEmpty()){Button od=new Button(this);od.setText("APRI DOCUMENTO");od.setOnClickListener(v->openUri(rec.docUri));box.addView(od);}
        CrmStore.Item target=rec;AlertDialog dlg=new AlertDialog.Builder(this).setTitle(p.n).setView(sv).setPositiveButton("SALVA",null).setNegativeButton("CHIUDI",null).create();dlg.setOnShowListener(v->dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(q->{target.status=CrmStore.STATUSES[st.getSelectedItemPosition()];target.phone=phone.getText().toString().trim();target.address=address.getText().toString().trim();target.website=web.getText().toString().trim();target.comment=currentCommentEdit.getText().toString().trim();target.nextContact=nextTime[0];target.key=currentRecordKey;target.name=p.n;target.lat=p.la;target.lon=p.lo;target.category=currentCategory();store.save(target);if(target.nextContact>System.currentTimeMillis())scheduleReminder(target);autoBackup();radar.invalidate();status.setText("Scheda salvata: "+p.n);dlg.dismiss();}));dlg.show();
    }

    EditText field(String hint,String value){EditText e=new EditText(this);e.setHint(hint);e.setText(value);e.setSingleLine(true);return e;}
    void pickDateTime(long[] out,TextView label){Calendar c=Calendar.getInstance();new DatePickerDialog(this,(v,y,m,d)->new TimePickerDialog(this,(tv,h,min)->{c.set(y,m,d,h,min,0);out[0]=c.getTimeInMillis();label.setText("Prossimo contatto: "+DateFormat.getDateTimeInstance().format(c.getTime()));},c.get(Calendar.HOUR_OF_DAY),c.get(Calendar.MINUTE),true).show(),c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();}
    void scheduleReminder(CrmStore.Item i){Intent in=new Intent(this,ReminderReceiver.class);in.putExtra("name",i.name);PendingIntent pi=PendingIntent.getBroadcast(this,Math.abs(i.key.hashCode()),in,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);AlarmManager am=(AlarmManager)getSystemService(ALARM_SERVICE);am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,i.nextContact,pi);}
    void startVoice(){try{Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,"it-IT");i.putExtra(RecognizerIntent.EXTRA_PROMPT,"Detta la nota della visita");startActivityForResult(i,201);}catch(Exception e){Toast.makeText(this,"Dettatura non disponibile",Toast.LENGTH_SHORT).show();}}
    void pickFile(boolean image){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(image?"image/*":"*/*");startActivityForResult(i,image?202:203);}
    void openCall(String n){if(n.trim().isEmpty()){Toast.makeText(this,"Inserisci il telefono",Toast.LENGTH_SHORT).show();return;}startActivity(new Intent(Intent.ACTION_DIAL,Uri.parse("tel:"+Uri.encode(n))));}
    void openWhatsApp(String n,String name){if(n.trim().isEmpty()){Toast.makeText(this,"Inserisci il telefono",Toast.LENGTH_SHORT).show();return;}String num=n.replaceAll("[^0-9]","");Uri u=Uri.parse("https://wa.me/"+num+"?text="+Uri.encode("Buongiorno, contatto "+name));startActivity(new Intent(Intent.ACTION_VIEW,u));}
    void openNav(Place p){startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("geo:"+p.la+","+p.lo+"?q="+p.la+","+p.lo+"("+Uri.encode(p.n)+")")));}
    void openUri(String s){try{Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse(s));i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(i);}catch(Exception e){Toast.makeText(this,"File non disponibile",Toast.LENGTH_SHORT).show();}}

    @Override protected void onActivityResult(int r,int code,Intent data){super.onActivityResult(r,code,data);if(code!=RESULT_OK||data==null)return;try{if(r==201){ArrayList<String>x=data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);if(x!=null&&!x.isEmpty()&&currentCommentEdit!=null){String old=currentCommentEdit.getText().toString();currentCommentEdit.setText(old+(old.isEmpty()?"":"\n")+x.get(0));}}else if(r==202||r==203){Uri u=data.getData();getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);CrmStore.Item i=store.get(currentRecordKey);i.key=currentRecordKey;i.name=currentPlace==null?i.name:currentPlace.n;if(r==202)i.photo=u.toString();else i.docUri=u.toString();store.save(i);autoBackup();Toast.makeText(this,r==202?"Foto collegata":"Documento collegato",Toast.LENGTH_SHORT).show();}else if(r==204){importCsv(data.getData());}else if(r==205){try(OutputStream o=getContentResolver().openOutputStream(data.getData())){o.write(pendingExport);}Toast.makeText(this,"Esportazione completata",Toast.LENGTH_SHORT).show();}}catch(Exception e){Toast.makeText(this,"Operazione non riuscita",Toast.LENGTH_LONG).show();}}

    void showArchive(){ArrayList<CrmStore.Item> rows=store.all();LinearLayout root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setPadding(18,8,18,8);EditText q=new EditText(this);q.setHint("Cerca nome, città, stato o nota");root.addView(q);ScrollView sv=new ScrollView(this);LinearLayout list=new LinearLayout(this);list.setOrientation(LinearLayout.VERTICAL);sv.addView(list);root.addView(sv,new LinearLayout.LayoutParams(-1,0,1));Runnable fill=()->{list.removeAllViews();String f=q.getText().toString().toLowerCase(Locale.ITALY);for(CrmStore.Item i:rows){String hay=(i.name+" "+i.city+" "+i.status+" "+i.comment).toLowerCase(Locale.ITALY);if(!hay.contains(f))continue;TextView v=new TextView(this);v.setText(i.name+"\n"+pretty(i.status)+(i.nextContact>0?" • richiamo "+DateFormat.getDateInstance(DateFormat.SHORT).format(new Date(i.nextContact)):"")+(i.comment.isEmpty()?"":"\n"+i.comment));v.setTextColor(Color.BLACK);v.setBackgroundColor(Color.WHITE);v.setPadding(14,12,14,12);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,0,0,8);list.addView(v,lp);}};q.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){fill.run();}public void afterTextChanged(Editable e){}});fill.run();new AlertDialog.Builder(this).setTitle("Archivio CRM ("+rows.size()+")").setView(root).setPositiveButton("CHIUDI",null).show();}
    void showStats(){int[] c=new int[CrmStore.STATUSES.length];for(CrmStore.Item i:store.all()){int k=Arrays.asList(CrmStore.STATUSES).indexOf(i.status);if(k>=0)c[k]++;}StringBuilder s=new StringBuilder();for(int i=0;i<c.length;i++)s.append(pretty(CrmStore.STATUSES[i])).append(": ").append(c[i]).append("\n");new AlertDialog.Builder(this).setTitle("Statistiche commerciali").setMessage(s.toString()).setPositiveButton("OK",null).show();}

    void openOptimizedRoute(){Location c=radarCenter!=null?radarCenter:here;if(c==null||radar.places.isEmpty())return;ArrayList<Place> left=new ArrayList<>();for(Place p:radar.places){String s=store.get(placeKey(p)).status;if(!"CLIENTE".equals(s)&&!"NON_INTERESSATO".equals(s))left.add(p);}ArrayList<Place> route=new ArrayList<>();double la=c.getLatitude(),lo=c.getLongitude();while(!left.isEmpty()&&route.size()<10){Place best=null;float bd=Float.MAX_VALUE;for(Place p:left){float[]d=new float[1];Location.distanceBetween(la,lo,p.la,p.lo,d);if(d[0]<bd){bd=d[0];best=p;}}route.add(best);left.remove(best);la=best.la;lo=best.lo;}if(route.isEmpty()){Toast.makeText(this,"Nessuna attività da inserire nel percorso",Toast.LENGTH_SHORT).show();return;}StringBuilder u=new StringBuilder("https://www.google.com/maps/dir/?api=1&origin=").append(c.getLatitude()).append(",").append(c.getLongitude()).append("&destination=").append(route.get(route.size()-1).la).append(",").append(route.get(route.size()-1).lo);if(route.size()>1){u.append("&waypoints=");for(int i=0;i<route.size()-1;i++){if(i>0)u.append("%7C");u.append(route.get(i).la).append(",").append(route.get(i).lo);}}startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(u.toString())));}

    void importCompanies(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("text/*");startActivityForResult(i,204);}
    void importCsv(Uri u)throws Exception{BufferedReader br=new BufferedReader(new InputStreamReader(getContentResolver().openInputStream(u)));String line;int n=0;while((line=br.readLine())!=null){if(line.trim().isEmpty())continue;String[] p=line.split(";");if(p.length<3)p=line.split(",");if(p.length<3)continue;try{CrmStore.Custom x=new CrmStore.Custom();x.name=p[0].trim();x.lat=Double.parseDouble(p[1].trim().replace(',','.'));x.lon=Double.parseDouble(p[2].trim().replace(',','.'));x.phone=p.length>3?p[3].trim():"";x.address=p.length>4?p[4].trim():"";x.website=p.length>5?p[5].trim():"";x.category=p.length>6?p[6].trim():"";x.city=p.length>7?p[7].trim():"";x.key=x.name+"@"+String.format(Locale.US,"%.5f,%.5f",x.lat,x.lon);store.saveCustom(x);CrmStore.Item it=store.get(x.key);it.key=x.key;it.name=x.name;it.lat=x.lat;it.lon=x.lon;it.phone=x.phone;it.address=x.address;it.website=x.website;it.category=x.category;it.city=x.city;store.save(it);n++;}catch(Exception ignored){}}br.close();autoBackup();Toast.makeText(this,"Importate "+n+" aziende",Toast.LENGTH_LONG).show();}

    void chooseExport(){new AlertDialog.Builder(this).setTitle("Esporta archivio").setItems(new String[]{"CSV","Excel (.xls)","PDF","Backup JSON"},(d,w)->{try{if(w==0)prepareExport(csv().getBytes("UTF-8"),"text/csv","RadarAttivita.csv");else if(w==1)prepareExport(xls().getBytes("UTF-8"),"application/vnd.ms-excel","RadarAttivita.xls");else if(w==2)prepareExport(pdf(),"application/pdf","RadarAttivita.pdf");else prepareExport(backupJson().getBytes("UTF-8"),"application/json","RadarAttivita-backup.json");}catch(Exception e){}}).show();}
    void prepareExport(byte[] b,String mime,String name){pendingExport=b;pendingMime=mime;pendingName=name;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType(mime);i.putExtra(Intent.EXTRA_TITLE,name);startActivityForResult(i,205);}
    String csv(){StringBuilder s=new StringBuilder("Nome;Stato;Telefono;Indirizzo;Sito;Categoria;Citta;Lat;Lon;Prossimo contatto;Commenti\n");for(CrmStore.Item i:store.all())s.append(clean(i.name)).append(';').append(i.status).append(';').append(clean(i.phone)).append(';').append(clean(i.address)).append(';').append(clean(i.website)).append(';').append(clean(i.category)).append(';').append(clean(i.city)).append(';').append(i.lat).append(';').append(i.lon).append(';').append(i.nextContact).append(';').append(clean(i.comment)).append('\n');return s.toString();}
    String clean(String s){return s==null?"":s.replace(";",",").replace("\n"," ");}
    String xls(){StringBuilder s=new StringBuilder("<html><meta charset='utf-8'><table border='1'><tr><th>Nome</th><th>Stato</th><th>Telefono</th><th>Indirizzo</th><th>Categoria</th><th>Commenti</th></tr>");for(CrmStore.Item i:store.all())s.append("<tr><td>").append(clean(i.name)).append("</td><td>").append(i.status).append("</td><td>").append(clean(i.phone)).append("</td><td>").append(clean(i.address)).append("</td><td>").append(clean(i.category)).append("</td><td>").append(clean(i.comment)).append("</td></tr>");return s.append("</table></html>").toString();}
    byte[] pdf()throws Exception{android.graphics.pdf.PdfDocument doc=new android.graphics.pdf.PdfDocument();Paint p=new Paint();p.setTextSize(11);int page=1,y=35;android.graphics.pdf.PdfDocument.Page pg=doc.startPage(new android.graphics.pdf.PdfDocument.PageInfo.Builder(595,842,page).create());Canvas c=pg.getCanvas();for(CrmStore.Item i:store.all()){String line=i.name+" | "+pretty(i.status)+" | "+i.phone+" | "+i.comment;if(y>810){doc.finishPage(pg);pg=doc.startPage(new android.graphics.pdf.PdfDocument.PageInfo.Builder(595,842,++page).create());c=pg.getCanvas();y=35;}c.drawText(line.length()>90?line.substring(0,90):line,25,y,p);y+=18;}doc.finishPage(pg);ByteArrayOutputStream o=new ByteArrayOutputStream();doc.writeTo(o);doc.close();return o.toByteArray();}
    String backupJson()throws Exception{JSONArray a=new JSONArray();for(CrmStore.Item i:store.all()){JSONObject o=new JSONObject();o.put("key",i.key);o.put("name",i.name);o.put("status",i.status);o.put("comment",i.comment);o.put("phone",i.phone);o.put("website",i.website);o.put("address",i.address);o.put("nextContact",i.nextContact);o.put("photo",i.photo);o.put("docUri",i.docUri);o.put("category",i.category);o.put("city",i.city);o.put("lat",i.lat);o.put("lon",i.lon);a.put(o);}return a.toString(2);}
    void autoBackup(){try{File f=new File(getFilesDir(),"radar_crm_backup.json");try(FileOutputStream o=new FileOutputStream(f)){o.write(backupJson().getBytes("UTF-8"));}}catch(Exception ignored){}}

    void checkNearby(){if(here==null)return;for(CrmStore.Item i:store.all()){if(Double.isNaN(i.lat)||Double.isNaN(i.lon)||"CLIENTE".equals(i.status)||"NON_INTERESSATO".equals(i.status))continue;float[]d=new float[1];Location.distanceBetween(here.getLatitude(),here.getLongitude(),i.lat,i.lon,d);if(d[0]<=500&&!nearbyShown.contains(i.key)){nearbyShown.add(i.key);Toast.makeText(this,"Vicino: "+i.name+" • "+Math.round(d[0])+" m",Toast.LENGTH_LONG).show();}}}

    @Override protected void onDestroy(){nearbyHandler.removeCallbacks(nearbyTask);super.onDestroy();}

    class RadarCrm extends RadarView{
        ArrayList<Hit> hits=new ArrayList<>();class Hit{float x,y;Place p;Hit(float a,float b,Place z){x=a;y=b;p=z;}}
        RadarCrm(Context c){super(c);setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_UP){Hit best=null;double bd=9999;for(Hit h:hits){double q=Math.hypot(e.getX()-h.x,e.getY()-h.y);if(q<bd){bd=q;best=h;}}if(best!=null&&bd<45){showSheet(best.p);return true;}}return true;});}
        protected void onDraw(Canvas c){float cx=getWidth()/2f,cy=getHeight()/2f,R=Math.min(getWidth(),getHeight())*.43f;p.setStyle(Paint.Style.FILL);p.setColor(Color.rgb(4,45,36));c.drawCircle(cx,cy,R,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(2);p.setColor(Color.rgb(20,150,110));for(int i=1;i<=4;i++)c.drawCircle(cx,cy,R*i/4,p);c.drawLine(cx-R,cy,cx+R,cy,p);c.drawLine(cx,cy-R,cx,cy+R,p);p.setStyle(Paint.Style.FILL);p.setColor(Color.WHITE);p.setTextSize(19);c.drawText("DAVANTI",cx-38,cy-R+22,p);p.setColor(Color.YELLOW);c.drawCircle(cx,cy,9,p);hits.clear();if(here==null)return;for(Place x:places){float[]res=new float[2];Location.distanceBetween(here.getLatitude(),here.getLongitude(),x.la,x.lo,res);if(res[0]>max)continue;CrmStore.Item rec=store.get(placeKey(x));if(!passFilter(rec.status))continue;double br=Math.toRadians(res[1]-heading);float rr=R*res[0]/max,dx=cx+(float)Math.sin(br)*rr,dy=cy-(float)Math.cos(br)*rr;p.setColor(statusColor(rec.status));c.drawCircle(dx,dy,11,p);hits.add(new Hit(dx,dy,x));}}
    }
}