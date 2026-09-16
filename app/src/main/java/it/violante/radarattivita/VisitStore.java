package it.violante.radarattivita;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;

class VisitStore extends SQLiteOpenHelper {
    static class Item {
        String key, name, comment;
        boolean visited;
        long updated;
        Item(String k,String n,boolean v,String c,long u){key=k;name=n;visited=v;comment=c;updated=u;}
    }

    VisitStore(Context c){super(c,"radar_visite.db",null,1);}

    @Override public void onCreate(SQLiteDatabase db){
        db.execSQL("CREATE TABLE IF NOT EXISTS visits(k TEXT PRIMARY KEY,n TEXT,visited INTEGER,comment TEXT,updated INTEGER)");
    }

    @Override public void onUpgrade(SQLiteDatabase db,int oldV,int newV){}

    Item get(String key){
        try(Cursor c=getReadableDatabase().query("visits",new String[]{"n","visited","comment","updated"},"k=?",new String[]{key},null,null,null)){
            if(c.moveToFirst()) return new Item(key,c.getString(0),c.getInt(1)==1,c.getString(2)==null?"":c.getString(2),c.getLong(3));
        }
        return new Item(key,"",false,"",0);
    }

    void save(String key,String name,boolean visited,String comment){
        ContentValues v=new ContentValues();
        v.put("k",key);v.put("n",name);v.put("visited",visited?1:0);v.put("comment",comment);v.put("updated",System.currentTimeMillis());
        getWritableDatabase().insertWithOnConflict("visits",null,v,SQLiteDatabase.CONFLICT_REPLACE);
    }

    ArrayList<Item> all(){
        ArrayList<Item> a=new ArrayList<>();
        try(Cursor c=getReadableDatabase().query("visits",new String[]{"k","n","visited","comment","updated"},null,null,null,null,"updated DESC")){
            while(c.moveToNext())a.add(new Item(c.getString(0),c.getString(1),c.getInt(2)==1,c.getString(3)==null?"":c.getString(3),c.getLong(4)));
        }
        return a;
    }
}
