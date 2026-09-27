package ir.orderbook.app;

import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import java.util.*;

public final class Store extends SQLiteOpenHelper {
    public static class Company {
        long id; String name, visitor, phone;
        public String toString() { return name; }
    }
    public static class Order {
        long id, company, buy, retail, days, cartons;
        String name, delivery, note, created;
    }
    public Store(Context c) { super(c, "orders.db", null, 1); }
    @Override public void onConfigure(SQLiteDatabase db) { db.setForeignKeyConstraintsEnabled(true); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE companies(id INTEGER PRIMARY KEY, name TEXT NOT NULL UNIQUE, visitor TEXT NOT NULL, phone TEXT NOT NULL)");
        db.execSQL("CREATE TABLE orders(id INTEGER PRIMARY KEY, company INTEGER NOT NULL REFERENCES companies(id), name TEXT NOT NULL, buy INTEGER NOT NULL CHECK(buy>0), retail INTEGER NOT NULL CHECK(retail>=0), days INTEGER NOT NULL CHECK(days>=0), cartons INTEGER NOT NULL CHECK(cartons>0), delivery TEXT NOT NULL, note TEXT NOT NULL, created TEXT NOT NULL)");
        db.execSQL("CREATE INDEX orders_company_date ON orders(company, created DESC, id DESC)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) { throw new IllegalStateException("Migration required"); }
    public List<Company> companies() {
        List<Company> list = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT * FROM companies ORDER BY name", null)) {
            while (c.moveToNext()) {
                Company x = new Company(); x.id=c.getLong(0); x.name=c.getString(1); x.visitor=c.getString(2); x.phone=c.getString(3); list.add(x);
            }
        } return list;
    }
    public long addCompany(String name, String visitor, String phone) {
        ContentValues v = new ContentValues(); v.put("name",name); v.put("visitor",visitor); v.put("phone",phone);
        return getWritableDatabase().insertOrThrow("companies",null,v);
    }
    public List<Order> orders(long company) {
        List<Order> list = new ArrayList<>();
        try (Cursor c=getReadableDatabase().rawQuery("SELECT * FROM orders WHERE company=? ORDER BY created DESC,id DESC",new String[]{String.valueOf(company)})) {
            while(c.moveToNext()) {
                Order o=new Order(); o.id=c.getLong(0); o.company=c.getLong(1); o.name=c.getString(2); o.buy=c.getLong(3); o.retail=c.getLong(4); o.days=c.getLong(5); o.cartons=c.getLong(6); o.delivery=c.getString(7); o.note=c.getString(8); o.created=c.getString(9); list.add(o);
            }
        } return list;
    }
    public void save(Order o) {
        ContentValues v=new ContentValues(); v.put("company",o.company);v.put("name",o.name);v.put("buy",o.buy);v.put("retail",o.retail);v.put("days",o.days);v.put("cartons",o.cartons);v.put("delivery",o.delivery);v.put("note",o.note);v.put("created",o.created);
        if(o.id==0) getWritableDatabase().insertOrThrow("orders",null,v);
        else getWritableDatabase().update("orders",v,"id=? AND company=?",new String[]{""+o.id,""+o.company});
    }
    public void delete(long id) { getWritableDatabase().delete("orders","id=?",new String[]{""+id}); }
}
