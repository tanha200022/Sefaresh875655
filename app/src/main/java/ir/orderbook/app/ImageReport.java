package ir.orderbook.app;

import android.content.Context;
import android.graphics.*;
import android.text.*;
import java.io.*;
import java.util.*;

/** Company-branded, paginated report. Twenty-unit body text rendered at 2x. */
public final class ImageReport {
    private static final int WIDTH=540,HEIGHT=1200,PAD=28,BOTTOM=1128;
    private final List<File> files=new ArrayList<>();
    private final Store.Company company;private final File directory;private final int count;
    private Bitmap bitmap;private Canvas canvas;private float y;private String date="";
    private final Paint paint=new Paint(3);
    private ImageReport(Context context,Store.Company company,int count)throws IOException{
        this.company=company;this.count=count;
        directory=new File(new File(context.getCacheDir(),"reports"),UUID.randomUUID().toString());
        if(!directory.mkdirs())throw new IOException("Cannot create report directory");
    }
    public static List<File> create(Context context,Store.Company company,List<Store.Order> orders)throws IOException{
        ImageReport r=new ImageReport(context,company,orders.size());
        try{r.start();int index=0;
            for(Store.Order o:orders){
                r.date=o.created;
                StaticLayout title=r.layout(fa((++index)+". ")+o.name,23,Ui.INK,true,WIDTH-2*PAD-32);
                int minimum=title.getHeight()+60;
                if(r.y+minimum>BOTTOM){r.finish();r.start();}
                r.block("ثبت سفارش: "+fa(o.created),20,Ui.GREEN,true,Ui.MINT);
                r.block(fa(index+". ")+o.name,23,Ui.INK,true,Ui.WHITE);
                r.block("قیمت خرید: "+fa(Values.money(o.buy))+" تومان",20,Ui.INK,false,Ui.WHITE);
                r.block("قیمت مصرف: "+fa(Values.money(o.retail))+" تومان",20,Ui.INK,false,Ui.WHITE);
                r.block("اختلاف نسبت به خرید: "+fa(Values.percent(o.buy,o.retail)),20,o.retail>=o.buy?Ui.GREEN:Ui.RED,true,Ui.WHITE);
                r.block("تعداد سفارش: "+fa(Values.money(o.cartons))+" کارتن",20,Ui.INK,false,Ui.WHITE);
                r.block("مدت تسویه: "+fa(Values.money(o.days))+" روز",20,Ui.INK,false,Ui.WHITE);
                r.block("تاریخ تحویل: "+fa(o.delivery),20,Ui.INK,false,Ui.WHITE);
                if(!o.note.isEmpty())r.block("توضیحات: "+o.note,20,Ui.MUTED,false,Ui.WHITE);
                r.y+=18;
            }r.finish();return r.files;
        }finally{if(r.bitmap!=null&&!r.bitmap.isRecycled())r.bitmap.recycle();}
    }
    static String fa(String s){StringBuilder b=new StringBuilder();for(char c:s.toCharArray())b.append(c>='0'&&c<='9'?(char)('۰'+c-'0'):c);return b.toString();}
    private StaticLayout layout(String text,int size,int color,boolean bold,int width){TextPaint p=new TextPaint(3);p.setTextSize(size);p.setColor(color);p.setTypeface(Typeface.create(bold?"sans-serif-medium":"sans-serif",Typeface.NORMAL));return StaticLayout.Builder.obtain(text,0,text.length(),p,width).setAlignment(Layout.Alignment.ALIGN_NORMAL).setTextDirection(TextDirectionHeuristics.FIRSTSTRONG_RTL).setLineSpacing(5,1).setIncludePad(true).build();}
    private void place(StaticLayout l,float x,float yy){canvas.save();canvas.translate(x,yy);l.draw(canvas);canvas.restore();}
    private void start(){bitmap=Bitmap.createBitmap(WIDTH*2,HEIGHT*2,Bitmap.Config.ARGB_8888);canvas=new Canvas(bitmap);canvas.scale(2,2);canvas.drawColor(Ui.BG);
        StaticLayout name=layout(company.name.replaceAll("\\s+"," "),30,Ui.WHITE,true,WIDTH-2*PAD-32);
        StaticLayout visitor=layout("ویزیتور: "+company.visitor.replaceAll("\\s+"," ")+"\nتلفن: "+fa(company.phone),20,0xFFD7EADD,false,WIDTH-2*PAD-32);
        float headerHeight=76+name.getHeight()+visitor.getHeight()+16;
        paint.setShader(new LinearGradient(PAD,20,WIDTH-PAD,headerHeight,new int[]{Ui.DARK,0xFF236B56},null,Shader.TileMode.CLAMP));canvas.drawRoundRect(PAD,24,WIDTH-PAD,24+headerHeight,24,24,paint);paint.setShader(null);
        place(layout("سفارش‌یار  /  لیست سفارش",20,Ui.GOLD,true,WIDTH-2*PAD-32),PAD+16,42);
        place(name,PAD+16,82);place(visitor,PAD+16,90+name.getHeight());
        y=24+headerHeight+20;
        StaticLayout sub=layout(fa(count+" قلم سفارش")+"  •  مبالغ به تومان",20,Ui.MUTED,false,WIDTH-2*PAD);place(sub,PAD,y);y+=sub.getHeight()+18;
        if(!files.isEmpty()&&!date.isEmpty()){StaticLayout continued=layout("ادامهٔ سفارش‌ها · "+fa(date),20,Ui.GREEN,true,WIDTH-2*PAD);place(continued,PAD,y);y+=continued.getHeight()+12;}
    }
    private void block(String text,int size,int color,boolean bold,int background)throws IOException{
        StaticLayout l=layout(text,size,color,bold,WIDTH-2*PAD-32);int start=0;
        while(start<l.getLineCount()){
            int top=l.getLineTop(start),end=start;
            while(end<l.getLineCount()&&y+12+l.getLineBottom(end)-top<=BOTTOM)end++;
            if(end==start){finish();start();continue;}
            int bottom=l.getLineBottom(end-1),h=bottom-top;
            paint.setColor(background);canvas.drawRoundRect(PAD,y,WIDTH-PAD,y+h+12,8,8,paint);
            canvas.save();canvas.clipRect(PAD+16,y+6,WIDTH-PAD-16,y+6+h);canvas.translate(PAD+16,y+6-top);l.draw(canvas);canvas.restore();y+=h+12;
            start=end;if(start<l.getLineCount()){finish();start();}
        }
    }
    private void finish()throws IOException{
        paint.setColor(Ui.LINE);canvas.drawLine(PAD,1150,WIDTH-PAD,1150,paint);
        place(layout("سفارش‌یار  •  صفحهٔ "+fa(""+(files.size()+1)),20,Ui.MUTED,false,WIDTH-2*PAD),PAD,1160);
        File file=new File(directory,ReportNames.stem(company.name)+"_"+Values.today().replace('/','-')+"_صفحه-"+String.format(Locale.US,"%02d",files.size()+1)+".png");
        try(OutputStream out=new FileOutputStream(file)){if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("PNG failed");}finally{bitmap.recycle();}files.add(file);
    }
}
