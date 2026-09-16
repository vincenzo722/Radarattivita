package it.violante.radarattivita;

import android.content.*;
import android.database.*;
import android.database.sqlite.*;
import java.util.*;

class CrmStore extends SQLiteOpenHelper {
    static final String[] STATUSES={"DA_VISITARE","VISITATA","INTERESSATO","DA_RICHIAMARE","CLIENTE","NON_INTERESSATO"};
    static class Item{
        String key="",name="",status="DA_VISITARE",comment="",phone="",website="",address="",photo="",docUri="",category="",city="";
        long updated=0,nextContact=0; double lat=Double.NaN,lon=Double.NaN;
    }
    static class Custom{String key="",name="",phone="",website="",address="",category="",city="";double lat,lon;}
    CrmStore(Context c){super(c,"radar_visite.db",null,2);}
    public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS visits(k TEXT PRIMARY KEY,n TEXT,visited INTEGER,comment TEXT,updated INTEGER,status TEXT,phone TEXT,website TEXT,address TEXT,next_contact INTEGER,photo TEXT,doc_uri TEXT,category TEXT,lat REAL,lon REAL,city TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS custom(k TEXT PRIMARY KEY,n TEXT,lat REAL,lon REAL,phone TEXT,website TEXT,address TEXT,category TEXT,city TEXT)");
    }
    public void onUpgrade(SQLiteDatabase db,int oldV,int newV){
        if(oldV<2){String[] q={"ALTER TABLE visits ADD COLUMN status TEXT","ALTER TABLE visits ADD COLUMN phone TEXT","ALTER TABLE visits ADD COLUMN website TEXT","ALTER TABLE visits ADD COLUMN address TEXT","ALTER TABLE visits ADD COLUMN next_contact INTEGER DEFAULT 0","ALTER TABLE visits ADD COLUMN photo TEXT","ALTER TABLE visits ADD COLUMN doc_uri TEXT","ALTER TABLE visits ADD COLUMN category TEXT","ALTER TABLE visits ADD COLUMN lat REAL","ALTER TABLE visits ADD COLUMN lon REAL","ALTER TABLE visits ADD COLUMN city TEXT"};for(String s:q)try{db.execSQL(s);}catch(Exception ignored){} try{db.execSQL("UPDATE visits SET status=CASE WHEN visited=1 THEN 'VISITATA' ELSE 'DA_VISITARE' END WHERE status IS NULL OR status=''");}catch(Exception ignored){} try{db.execSQL("CREATE TABLE IF NOT EXISTS custom(k TEXT PRIMARY KEY,n TEXT,lat REAL,lon REAL,phone TEXT,website TEXT,address TEXT,category TEXT,city TEXT)");}catch(Exception ignored){}}
    }
    static String nn(String s){return s==null?"":s;}
    Item get(String key){Item i=new Item();i.key=key;try(Cursor c=getReadableDatabase().query("visits",new String[]{"n","visited","comment","updated","status","phone","website","address","next_contact","photo","doc_uri","category","lat","lon","city"},"k=?",new String[]{key},null,null,null)){if(c.moveToFirst()){i.name=nn(c.getString(0));i.comment=nn(c.getString(2));i.updated=c.getLong(3);i.status=nn(c.getString(4));if(i.status.isEmpty())i.status=c.getInt(1)==1?"VISITATA":"DA_VISITARE";i.phone=nn(c.getString(5));i.website=nn(c.getString(6));i.address=nn(c.getString(7));i.nextContact=c.getLong(8);i.photo=nn(c.getString(9));i.docUri=nn(c.getString(10));i.category=nn(c.getString(11));if(!c.isNull(12))i.lat=c.getDouble(12);if(!c.isNull(13))i.lon=c.getDouble(13);i.city=nn(c.getString(14));}}recover(i);return i;}
    void recover(Item i){if(!Double.isNaN(i.lat)&&!Double.isNaN(i.lon))return;try{int a=i.key.lastIndexOf('@');String[] p=i.key.substring(a+1).split(",");i.lat=Double.parseDouble(p[0]);i.lon=Double.parseDouble(p[1]);}catch(Exception ignored){}}
    void save(Item i){ContentValues v=new ContentValues();v.put("k",i.key);v.put("n",i.name);v.put("visited",i.status.equals("DA_VISITARE")?0:1);v.put("comment",i.comment);v.put("updated",System.currentTimeMillis());v.put("status",i.status);v.put("phone",i.phone);v.put("website",i.website);v.put("address",i.address);v.put("next_contact",i.nextContact);v.put("photo",i.photo);v.put("doc_uri",i.docUri);v.put("category",i.category);if(!Double.isNaN(i.lat))v.put("lat",i.lat);if(!Double.isNaN(i.lon))v.put("lon",i.lon);v.put("city",i.city);getWritableDatabase().insertWithOnConflict("visits",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    ArrayList<Item> all(){ArrayList<Item>a=new ArrayList<>();try(Cursor c=getReadableDatabase().query("visits",new String[]{"k"},null,null,null,null,"updated DESC")){while(c.moveToNext())a.add(get(c.getString(0)));}return a;}
    void saveCustom(Custom x){ContentValues v=new ContentValues();v.put("k",x.key);v.put("n",x.name);v.put("lat",x.lat);v.put("lon",x.lon);v.put("phone",x.phone);v.put("website",x.website);v.put("address",x.address);v.put("category",x.category);v.put("city",x.city);getWritableDatabase().insertWithOnConflict("custom",null,v,SQLiteDatabase.CONFLICT_REPLACE);}
    ArrayList<Custom> customs(){ArrayList<Custom>a=new ArrayList<>();try(Cursor c=getReadableDatabase().query("custom",new String[]{"k","n","lat","lon","phone","website","address","category","city"},null,null,null,null,"n")){while(c.moveToNext()){Custom x=new Custom();x.key=nn(c.getString(0));x.name=nn(c.getString(1));x.lat=c.getDouble(2);x.lon=c.getDouble(3);x.phone=nn(c.getString(4));x.website=nn(c.getString(5));x.address=nn(c.getString(6));x.category=nn(c.getString(7));x.city=nn(c.getString(8));a.add(x);}}return a;}
}