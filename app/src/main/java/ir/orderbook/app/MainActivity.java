package ir.orderbook.app;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.content.res.ColorStateList;
import android.database.sqlite.SQLiteConstraintException;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import androidx.core.content.FileProvider;
import java.io.*;
import java.math.BigInteger;
import java.util.*;

public class MainActivity extends Activity {
    private Ui ui;private Store db;private LinearLayout root,content;private ScrollView scroll;
    private List<Store.Company> companies=new ArrayList<>();private final List<Store.Order> visible=new ArrayList<>();
    private long selected=-1;private String dateFilter="همهٔ تاریخ‌ها",pendingFile;private boolean exporting;
    private Button exportButton;private AlertDialog busyDialog;private Dialog activeDialog;
    private Bundle draft;private String draftKind;private Store.Order editing;private final List<EditText> fields=new ArrayList<>();
    @Override public void onCreate(Bundle state){
        super.onCreate(state);ui=new Ui(this);db=new Store(this);
        if(state!=null){selected=state.getLong("company",-1);dateFilter=state.getString("filter","همهٔ تاریخ‌ها");pendingFile=state.getString("pending");draft=state.getBundle("draft");}
        else selected=getPreferences(0).getLong("company",-1);
        root=ui.col();root.setBackgroundColor(Ui.BG);
        root.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});
        getWindow().setStatusBarColor(Ui.BG);getWindow().setNavigationBarColor(Ui.WHITE);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|(android.os.Build.VERSION.SDK_INT>=26?View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0));
        setContentView(root);root.requestApplyInsets();
        LinearLayout toolbar=ui.row();toolbar.setPadding(ui.dp(20),ui.dp(12),ui.dp(20),ui.dp(12));
        ImageView logo=new ImageView(this);logo.setImageDrawable(new Ui.Icon("receipt",Ui.GREEN));toolbar.addView(logo,new LinearLayout.LayoutParams(ui.dp(28),ui.dp(28)));ui.space(toolbar,10);
        ui.weighted(toolbar,ui.text("سفارش‌یار",24,Ui.INK,true));toolbar.addView(ui.text(fa(Values.today()),20,Ui.MUTED,false));root.addView(toolbar);
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);
        content=ui.col();content.setPadding(ui.dp(20),ui.dp(4),ui.dp(20),ui.dp(20));scroll.addView(content);root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout dock=ui.row();dock.setPadding(ui.dp(16),ui.dp(12),ui.dp(16),ui.dp(12));dock.setBackgroundColor(Ui.WHITE);dock.setElevation(ui.dp(12));
        Button add=ui.button("کالای جدید","plus",true);exportButton=ui.button("عکس و ارسال","share",false);
        ui.weighted(dock,add);ui.space(dock,10);ui.weighted(dock,exportButton);root.addView(dock);
        add.setOnClickListener(v->{if(company()==null)companyDialog();else orderDialog(null);});exportButton.setOnClickListener(v->export());
        refresh();
        if(draft!=null){Bundle restored=draft;root.post(()->{if("company".equals(restored.getString("kind")))companyDialog();else{Store.Order old=null;long id=restored.getLong("id",0);for(Store.Order o:db.orders(selected))if(o.id==id)old=o;if(id==0||old!=null)orderDialog(old);}ArrayList<String> values=restored.getStringArrayList("values");if(values!=null)for(int i=0;i<Math.min(values.size(),fields.size());i++)fields.get(i).setText(values.get(i));draft=null;});}
    }
    @Override public void onSaveInstanceState(Bundle b){super.onSaveInstanceState(b);b.putLong("company",selected);b.putString("filter",dateFilter);b.putString("pending",pendingFile);
        if(activeDialog!=null&&activeDialog.isShowing()&&draftKind!=null){Bundle d=new Bundle();d.putString("kind",draftKind);d.putLong("id",editing==null?0:editing.id);ArrayList<String> values=new ArrayList<>();for(EditText e:fields)values.add(e.getText().toString());d.putStringArrayList("values",values);b.putBundle("draft",d);}}
    private static String fa(String s){StringBuilder b=new StringBuilder();for(char c:s.toCharArray())b.append(c>='0'&&c<='9'?(char)('۰'+c-'0'):c);return b.toString();}
    private TextView text(String s,boolean bold){return ui.text(s,20,Ui.INK,bold);}
    private Store.Company company(){for(Store.Company c:companies)if(c.id==selected)return c;return null;}
    private void refresh(){companies=db.companies();if(company()==null)selected=companies.isEmpty()?-1:companies.get(0).id;getPreferences(0).edit().putLong("company",selected).apply();render();}
    private boolean wide(){return getResources().getConfiguration().screenWidthDp>=400&&getResources().getConfiguration().fontScale<1.2f;}
    private void render(){int oldY=scroll.getScrollY();content.removeAllViews();visible.clear();Store.Company c=company();
        LinearLayout companyCard=ui.col();ui.pad(companyCard,22);
        GradientDrawable gradient=new GradientDrawable(GradientDrawable.Orientation.TR_BL,new int[]{Ui.DARK,0xFF215E50});gradient.setCornerRadius(ui.dp(28));companyCard.setBackground(gradient);
        LinearLayout head=ui.row();ui.weighted(head,ui.text("شرکت انتخاب‌شده",20,0xFFB9D9CB,false));ImageButton plus=ui.iconButton("plus","افزودن شرکت",Ui.WHITE);head.addView(plus);plus.setOnClickListener(v->companyDialog());companyCard.addView(head);
        LinearLayout choice=ui.row();TextView name=ui.text(c==null?"شرکت را انتخاب کنید":c.name,26,Ui.WHITE,true);ui.weighted(choice,name);choice.addView(ui.iconButton("down","انتخاب شرکت",Ui.GOLD));
        choice.setOnClickListener(v->companyPicker());for(int i=0;i<choice.getChildCount();i++)choice.getChildAt(i).setOnClickListener(v->companyPicker());companyCard.addView(choice);ui.gap(companyCard,14);
        if(c==null)companyCard.addView(ui.text("اولین شرکت را اضافه کنید\nو سفارش‌ها را یک‌جا داشته باشید.",20,0xFFDCEBE4,false));
        else{companyCard.addView(ui.text("ویزیتور  ·  "+c.visitor,20,0xFFDCEBE4,false));TextView phone=ui.text(fa(c.phone),20,Ui.GOLD,true);phone.setTextDirection(View.TEXT_DIRECTION_LTR);phone.setGravity(Gravity.RIGHT);companyCard.addView(phone);}
        content.addView(companyCard);ui.gap(content,22);
        List<Store.Order> all=db.orders(selected);List<String> dates=new ArrayList<>();dates.add("همهٔ تاریخ‌ها");for(Store.Order o:all)if(!dates.contains(o.created))dates.add(o.created);
        if(!dates.contains(dateFilter))dateFilter=dates.get(0);for(Store.Order o:all)if(dateFilter.equals(dates.get(0))||o.created.equals(dateFilter))visible.add(o);
        BigInteger total=BigInteger.ZERO;for(Store.Order o:visible)total=total.add(BigInteger.valueOf(o.cartons));
        LinearLayout stats=ui.row();ui.weighted(stats,stat(fa(""+visible.size()),"قلم سفارش","receipt"));ui.space(stats,12);ui.weighted(stats,stat(fa(total.toString()),"کارتن سفارش","box"));content.addView(stats);ui.gap(content,24);
        LinearLayout section=ui.row();ui.weighted(section,ui.text("سفارش‌های شما",24,Ui.INK,true));section.addView(ui.tag(fa(""+visible.size()),Ui.GREEN,Ui.MINT));content.addView(section);ui.gap(content,12);
        HorizontalScrollView hs=new HorizontalScrollView(this);hs.setHorizontalScrollBarEnabled(false);hs.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);LinearLayout chips=ui.row();
        for(String date:dates){boolean on=date.equals(dateFilter);TextView chip=ui.tag(date.equals(dates.get(0))?"همهٔ تاریخ‌ها":fa(date),on?Ui.WHITE:Ui.MUTED,on?Ui.GREEN:Ui.WHITE);chip.setMinHeight(ui.dp(48));chip.setGravity(Gravity.CENTER);chip.setOnClickListener(v->{dateFilter=date;render();});chips.addView(chip);ui.space(chips,8);}hs.addView(chips);content.addView(hs);ui.gap(content,18);
        if(visible.isEmpty()){LinearLayout empty=ui.card();ui.pad(empty,24);ImageView icon=new ImageView(this);icon.setImageDrawable(new Ui.Icon("box",Ui.GREEN));LinearLayout.LayoutParams ilp=new LinearLayout.LayoutParams(ui.dp(64),ui.dp(64));ilp.gravity=Gravity.CENTER;empty.addView(icon,ilp);ui.gap(empty,16);TextView title=text("جای اولین سفارش شما",true);title.setGravity(Gravity.CENTER);empty.addView(title);ui.gap(empty,8);TextView hint=ui.text("از دکمهٔ پایین شروع کنید؛\nبقیهٔ کارها مرتب می‌ماند.",20,Ui.MUTED,false);hint.setGravity(Gravity.CENTER);empty.addView(hint);content.addView(empty);}
        String last="";for(Store.Order o:visible){if(!last.equals(o.created)){last=o.created;LinearLayout day=ui.row();ImageView cal=new ImageView(this);cal.setImageDrawable(new Ui.Icon("calendar",Ui.MUTED));day.addView(cal,new LinearLayout.LayoutParams(ui.dp(22),ui.dp(22)));ui.space(day,8);day.addView(ui.text("ثبت در "+fa(last),20,Ui.MUTED,false));content.addView(day);ui.gap(content,10);}content.addView(orderCard(o));ui.gap(content,14);}
        exportButton.setEnabled(!visible.isEmpty()&&!exporting);exportButton.setAlpha(visible.isEmpty()?0.45f:1f);scroll.post(()->scroll.scrollTo(0,oldY));
    }
    private LinearLayout stat(String n,String label,String icon){LinearLayout l=ui.card();LinearLayout top=ui.row();ui.weighted(top,ui.text(n,30,Ui.GREEN,true));ImageView i=new ImageView(this);i.setImageDrawable(new Ui.Icon(icon,Ui.GREEN));top.addView(i,new LinearLayout.LayoutParams(ui.dp(26),ui.dp(26)));l.addView(top);l.addView(ui.text(label,20,Ui.MUTED,false));return l;}
    private LinearLayout orderCard(Store.Order o){LinearLayout card=ui.card();LinearLayout title=ui.row();ui.weighted(title,ui.text(o.name,22,Ui.INK,true));ImageButton more=ui.iconButton("more","گزینه‌های "+o.name,Ui.MUTED);title.addView(more);card.addView(title);more.setOnClickListener(v->orderMenu(o));ui.gap(card,14);
        boolean columns=getResources().getConfiguration().fontScale<1.2f&&o.buy<100000000&&o.retail<100000000;LinearLayout prices=columns?ui.row():ui.col();LinearLayout buy=price("قیمت خرید",o.buy),retail=price("قیمت مصرف",o.retail);
        if(columns){ui.weighted(prices,buy);ui.space(prices,8);ui.weighted(prices,retail);}else{prices.addView(buy);ui.gap(prices,10);prices.addView(retail);}card.addView(prices);ui.gap(card,12);
        card.addView(ui.tag("اختلاف خرید و مصرف   "+fa(Values.percent(o.buy,o.retail)),o.retail>=o.buy?Ui.GREEN:Ui.RED,o.retail>=o.buy?Ui.MINT:0xFFFBEDE9));ui.gap(card,16);ui.rule(card);ui.gap(card,14);
        detail(card,"تعداد سفارش",fa(Values.money(o.cartons))+" کارتن");detail(card,"مدت تسویه",fa(Values.money(o.days))+" روز");detail(card,"تاریخ تحویل",fa(o.delivery));
        if(!o.note.isEmpty()){ui.gap(card,12);TextView note=ui.text(o.note,20,Ui.MUTED,false);note.setBackground(ui.bg(Ui.BG,14));ui.pad(note,12);card.addView(note);}return card;}
    private LinearLayout price(String label,long n){LinearLayout l=ui.col();l.addView(ui.text(label+" · تومان",20,Ui.MUTED,false));l.addView(ui.text(fa(Values.money(n)),24,Ui.INK,true));return l;}
    private void detail(LinearLayout l,String label,String value){LinearLayout row=ui.row();ui.weighted(row,ui.text(label,20,Ui.MUTED,false));TextView val=text(value,true);row.addView(val);l.addView(row);ui.gap(l,7);}
    private void companyPicker(){LinearLayout items=ui.col();for(Store.Company c:companies){LinearLayout item=ui.card();LinearLayout r=ui.row();ui.weighted(r,text(c.name,true));if(c.id==selected)r.addView(ui.tag("فعال",Ui.GREEN,Ui.MINT));item.addView(r);item.addView(ui.text(c.visitor,20,Ui.MUTED,false));item.setOnClickListener(v->{selected=c.id;dateFilter="همهٔ تاریخ‌ها";activeDialog.dismiss();refresh();scroll.smoothScrollTo(0,0);});items.addView(item);ui.gap(items,10);}Sheet s=sheet("شرکت‌های شما","برای دیدن سفارش‌ها، شرکت را انتخاب کنید.",items,"افزودن شرکت",false);s.save.setOnClickListener(v->{s.dialog.dismiss();companyDialog();});}
    private void orderMenu(Store.Order o){LinearLayout body=ui.col();Button edit=ui.button("ویرایش سفارش","edit",false),delete=ui.button("حذف سفارش","trash",false);delete.setTextColor(Ui.RED);body.addView(edit);ui.gap(body,12);body.addView(delete);Sheet s=sheet(o.name,"مدیریت این سفارش",body,null,false);edit.setOnClickListener(v->{s.dialog.dismiss();orderDialog(o);});delete.setOnClickListener(v->{s.dialog.dismiss();confirmDelete(o);});}
    private void confirmDelete(Store.Order o){LinearLayout b=ui.col();b.addView(text("سفارش «"+o.name+"» حذف شود؟",false));Sheet s=sheet("حذف سفارش","این کار قابل بازگشت نیست.",b,"حذف سفارش",false);s.save.setTextColor(Ui.WHITE);s.save.setBackground(ui.ripple(Ui.RED,18));s.save.setOnClickListener(v->{db.delete(o.id);s.dialog.dismiss();refresh();toast("سفارش حذف شد");});}
    private final class Sheet{Dialog dialog;Button save;}
    private Sheet sheet(String title,String subtitle,LinearLayout body,String action,boolean form){
        if(!form){draftKind=null;fields.clear();}Sheet s=new Sheet();s.dialog=new Dialog(this);s.dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);LinearLayout panel=ui.col();panel.setBackground(ui.bg(Ui.BG,28));
        View handle=new View(this);handle.setBackground(ui.bg(0xFFCBD6CF,3));LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(ui.dp(42),ui.dp(5));hp.gravity=Gravity.CENTER;hp.topMargin=ui.dp(12);hp.bottomMargin=ui.dp(12);panel.addView(handle,hp);
        LinearLayout heading=ui.row();heading.setPadding(ui.dp(20),0,ui.dp(20),0);ui.weighted(heading,ui.text(title,24,Ui.INK,true));ImageButton close=ui.iconButton("close","بستن",Ui.MUTED);heading.addView(close);close.setOnClickListener(v->s.dialog.dismiss());panel.addView(heading);
        if(subtitle!=null){TextView sub=ui.text(subtitle,20,Ui.MUTED,false);sub.setPadding(ui.dp(24),0,ui.dp(24),ui.dp(12));panel.addView(sub);}
        ScrollView sc=new ScrollView(this);sc.setFillViewport(false);sc.setVerticalScrollBarEnabled(false);body.setPadding(ui.dp(22),ui.dp(8),ui.dp(22),ui.dp(20));sc.addView(body);panel.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        if(action!=null){LinearLayout footer=ui.col();footer.setPadding(ui.dp(20),ui.dp(10),ui.dp(20),ui.dp(18));s.save=ui.button(action,form?"check":null,true);footer.addView(s.save,new LinearLayout.LayoutParams(-1,-2));panel.addView(footer);}
        panel.setOnApplyWindowInsetsListener((v,insets)->{v.setPadding(insets.getSystemWindowInsetLeft(),0,insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        s.dialog.setContentView(panel);Window w=s.dialog.getWindow();if(w!=null){w.setBackgroundDrawableResource(android.R.color.transparent);w.setGravity(Gravity.BOTTOM);w.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);WindowManager.LayoutParams attrs=w.getAttributes();attrs.dimAmount=.35f;w.setAttributes(attrs);w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);}
        s.dialog.show();if(w!=null){int width=Math.min(getResources().getDisplayMetrics().widthPixels,ui.dp(620));w.setLayout(width,(int)(getResources().getDisplayMetrics().heightPixels*.9f));}panel.requestApplyInsets();activeDialog=s.dialog;return s;
    }
    private void beginForm(String kind,Store.Order old){draftKind=kind;editing=old;fields.clear();}
    private EditText field(LinearLayout form,String label,String value,int type,int max,String hint){form.addView(ui.text(label,20,Ui.MUTED,false));ui.gap(form,6);EditText e=new EditText(this);e.setTextSize(20);e.setTextColor(Ui.INK);e.setHintTextColor(0xFF8C9C93);e.setHint(hint);e.setInputType(type);e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(max)});e.setBackground(ui.border(Ui.WHITE,16,Ui.LINE));e.setPadding(ui.dp(16),ui.dp(14),ui.dp(16),ui.dp(14));e.setMinHeight(ui.dp(58));e.setText(value);e.setSelectAllOnFocus(false);e.setOnFocusChangeListener((v,focused)->e.setBackground(ui.border(Ui.WHITE,16,focused?Ui.GREEN:Ui.LINE)));form.addView(e,new LinearLayout.LayoutParams(-1,-2));ui.gap(form,18);fields.add(e);return e;}
    private void grouping(EditText e){e.addTextChangedListener(new TextWatcher(){boolean busy;public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){}public void afterTextChanged(Editable t){if(busy)return;String raw=Values.ascii(t.toString()).replace(",", "").replace("٬", "");if(raw.isEmpty()||!raw.matches("[0-9]+"))return;try{String fmt=Values.money(Long.parseLong(raw));if(!fmt.equals(t.toString())){int old=e.getSelectionStart(),digits=0;for(int i=0;i<Math.min(old,t.length());i++)if(Character.isDigit(t.charAt(i)))digits++;busy=true;e.setText(fmt);int p=0,n=0;while(p<fmt.length()&&n<digits){if(Character.isDigit(fmt.charAt(p)))n++;p++;}e.setSelection(p);busy=false;}}catch(NumberFormatException ignored){busy=false;}}});}
    private String required(EditText e,String message){String s=e.getText().toString().trim();if(s.isEmpty()){e.setError(message);e.requestFocus();throw new IllegalArgumentException(message);}return s;}
    private void companyDialog(){beginForm("company",null);LinearLayout f=ui.col();EditText name=field(f,"نام شرکت","",1,100,"مثلاً شرکت پخش بهار"),visitor=field(f,"نام ویزیتور","",1,100,"نام و نام خانوادگی"),phone=field(f,"تلفن ویزیتور","",3,20,"09…");phone.setTextDirection(View.TEXT_DIRECTION_LTR);Sheet s=sheet("شرکت جدید","یک‌بار ثبت کنید؛ همیشه در دسترس است.",f,"ثبت شرکت",true);
        s.save.setOnClickListener(v->{try{String n=required(name,"نام شرکت را وارد کنید"),vis=required(visitor,"نام ویزیتور را وارد کنید"),p=Values.ascii(required(phone,"تلفن را وارد کنید")).replace(" ","").replace("-","");if(!p.matches("\\+?[0-9]{7,15}")){phone.setError("شماره معتبر وارد کنید");return;}selected=db.addCompany(n,vis,p);dateFilter="همهٔ تاریخ‌ها";endForm(s);refresh();toast("شرکت اضافه شد");}catch(SQLiteConstraintException e){name.setError("این شرکت قبلاً ثبت شده است");}catch(IllegalArgumentException e){toast(e.getMessage());}});
    }
    private void orderDialog(Store.Order old){beginForm("order",old);final long companyId=selected;LinearLayout f=ui.col();
        EditText name=field(f,"نام کالا",old==null?"":old.name,1,100,"نام کامل محصول");
        EditText buy=field(f,"قیمت خرید · تومان",old==null?"":Values.money(old.buy),2,18,"۰"),retail=field(f,"قیمت مصرف · تومان",old==null?"":Values.money(old.retail),2,18,"۰");grouping(buy);grouping(retail);
        TextView percent=ui.tag("اختلاف نسبت به خرید: —",Ui.GREEN,Ui.MINT);f.addView(percent);ui.gap(f,20);
        TextWatcher update=new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){}public void afterTextChanged(Editable e){try{percent.setText("اختلاف نسبت به خرید: "+fa(Values.percent(Values.number(buy.getText().toString(),1,999999999999L),Values.number(retail.getText().toString(),0,999999999999L))));}catch(IllegalArgumentException x){percent.setText("اختلاف نسبت به خرید: —");}}};buy.addTextChangedListener(update);retail.addTextChangedListener(update);update.afterTextChanged(buy.getText());
        EditText days=field(f,"مدت تسویه · روز",old==null?"":Values.money(old.days),2,8,"مثلاً ۳۰"),cartons=field(f,"تعداد سفارش · کارتن",old==null?"":Values.money(old.cartons),2,10,"مثلاً ۱۲");grouping(days);grouping(cartons);
        EditText delivery=field(f,"تاریخ تحویل · شمسی",old==null?Values.today():old.delivery,InputType.TYPE_CLASS_DATETIME|InputType.TYPE_DATETIME_VARIATION_DATE,10,"۱۴۰۵/۰۷/۰۴");delivery.setTextDirection(View.TEXT_DIRECTION_LTR);
        EditText note=field(f,"توضیحات · اختیاری",old==null?"":old.note,InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE,500,"نکته‌ای برای این سفارش…");note.setMinLines(3);note.setGravity(Gravity.TOP|Gravity.START);
        Sheet s=sheet(old==null?"سفارش جدید":"ویرایش سفارش",company()==null?"":company().name,f,old==null?"ثبت سفارش":"ذخیرهٔ تغییرات",true);
        s.save.setOnClickListener(v->{try{Store.Order o=new Store.Order();o.company=companyId;o.id=old==null?0:old.id;o.created=old==null?Values.today():old.created;o.name=required(name,"نام کالا را وارد کنید");o.buy=readNumber(buy,1,999999999999L);o.retail=readNumber(retail,0,999999999999L);o.days=readNumber(days,0,36500);o.cartons=readNumber(cartons,1,1000000);try{o.delivery=Values.date(delivery.getText().toString());}catch(IllegalArgumentException x){delivery.setError(x.getMessage());delivery.requestFocus();throw x;}o.note=note.getText().toString().trim();db.save(o);dateFilter="همهٔ تاریخ‌ها";endForm(s);refresh();toast("سفارش ذخیره شد");}catch(IllegalArgumentException x){toast(x.getMessage());}});
    }
    private void endForm(Sheet s){draftKind=null;fields.clear();editing=null;View focused=s.dialog.getCurrentFocus();if(focused!=null)((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(focused.getWindowToken(),0);s.dialog.dismiss();}
    private long readNumber(EditText e,long min,long max){try{return Values.number(e.getText().toString(),min,max);}catch(IllegalArgumentException x){e.setError(x.getMessage());e.requestFocus();throw x;}}
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    private void export(){if(exporting||visible.isEmpty())return;exporting=true;exportButton.setEnabled(false);Store.Company c=company();List<Store.Order> snapshot=new ArrayList<>(visible);Context app=getApplicationContext();
        LinearLayout progress=ui.col();ui.pad(progress,24);ProgressBar p=new ProgressBar(this);progress.addView(p);ui.gap(progress,16);progress.addView(text("در حال آماده‌سازی عکس سفارش‌ها…",false));busyDialog=new AlertDialog.Builder(this).setView(progress).setCancelable(false).create();busyDialog.show();
        new Thread(()->{try{List<File> files=ImageReport.create(app,c,snapshot);runOnUiThread(()->{exporting=false;if(isFinishing()||isDestroyed())return;busyDialog.dismiss();exportButton.setEnabled(true);preview(c,files);});}catch(Exception|OutOfMemoryError e){runOnUiThread(()->{exporting=false;if(isFinishing()||isDestroyed())return;busyDialog.dismiss();exportButton.setEnabled(true);toast("ساخت عکس انجام نشد؛ یک تاریخ را انتخاب کنید و دوباره تلاش کنید.");});}}).start();
    }
    private void preview(Store.Company company,List<File> files){LinearLayout body=ui.col();TextView filename=ui.text("",20,Ui.MUTED,false);body.addView(filename);ui.gap(body,12);
        ImageView image=new ImageView(this);image.setAdjustViewBounds(true);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setBackground(ui.border(Ui.WHITE,16,Ui.LINE));body.addView(image,new LinearLayout.LayoutParams(-1,-2));ui.gap(body,12);
        LinearLayout nav=ui.row();Button next=ui.button("بعدی","next",false),prev=ui.button("قبلی","prev",false);TextView page=text("",true);page.setGravity(Gravity.CENTER);nav.addView(prev,new LinearLayout.LayoutParams(0,-2,1));nav.addView(page,new LinearLayout.LayoutParams(0,-2,1));nav.addView(next,new LinearLayout.LayoutParams(0,-2,1));body.addView(nav);ui.gap(body,12);
        Button save=ui.button("ذخیرهٔ این عکس","save",false),shareOne=ui.button("اشتراک همین صفحه","share",false);body.addView(save);ui.gap(body,10);body.addView(shareOne);ui.gap(body,12);body.addView(ui.text("برای ارسال، ایتا را از فهرست برنامه‌ها انتخاب کنید.",20,Ui.MUTED,false));
        final int[] index={0};final Bitmap[] thumb={null};Runnable show=()->{BitmapFactory.Options opts=new BitmapFactory.Options();opts.inSampleSize=2;Bitmap b=BitmapFactory.decodeFile(files.get(index[0]).getAbsolutePath(),opts);image.setImageBitmap(b);Bitmap previous=thumb[0];thumb[0]=b;if(previous!=null)previous.recycle();filename.setText(files.get(index[0]).getName());page.setText(fa((index[0]+1)+" / "+files.size()));prev.setEnabled(index[0]>0);next.setEnabled(index[0]<files.size()-1);prev.setAlpha(prev.isEnabled()?1:.35f);next.setAlpha(next.isEnabled()?1:.35f);};
        Sheet s=sheet("عکس سفارش‌ها",company.name,body,files.size()>1?"اشتراک همهٔ عکس‌ها":"اشتراک‌گذاری عکس",false);draftKind=null;s.save.setOnClickListener(v->share(company,files));shareOne.setOnClickListener(v->share(company,Collections.singletonList(files.get(index[0]))));save.setOnClickListener(v->saveImage(files.get(index[0])));prev.setOnClickListener(v->{if(index[0]>0){index[0]--;show.run();}});next.setOnClickListener(v->{if(index[0]<files.size()-1){index[0]++;show.run();}});s.dialog.setOnDismissListener(d->{image.setImageDrawable(null);if(thumb[0]!=null){thumb[0].recycle();thumb[0]=null;}});show.run();
    }
    private void share(Store.Company c,List<File> files){try{ArrayList<Uri> uris=new ArrayList<>();for(File file:files)uris.add(FileProvider.getUriForFile(this,getPackageName()+".reports",file));Intent send=new Intent(uris.size()==1?Intent.ACTION_SEND:Intent.ACTION_SEND_MULTIPLE);send.setType("image/png");send.putExtra(Intent.EXTRA_SUBJECT,"سفارش‌های "+c.name);send.putExtra(Intent.EXTRA_TEXT,"لیست سفارش‌های "+c.name);if(uris.size()==1)send.putExtra(Intent.EXTRA_STREAM,uris.get(0));else send.putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris);
        ClipData clip=ClipData.newUri(getContentResolver(),"سفارش‌های "+c.name,uris.get(0));for(int i=1;i<uris.size();i++)clip.addItem(new ClipData.Item(uris.get(i)));send.setClipData(clip);send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);Intent chooser=Intent.createChooser(send,"ارسال عکس سفارش‌ها · ایتا یا سایر برنامه‌ها");chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(chooser);
        }catch(ActivityNotFoundException e){toast("برنامه‌ای برای اشتراک عکس پیدا نشد");}catch(IllegalArgumentException e){toast("عکس در دسترس نیست؛ خروجی را دوباره بسازید");}}
    private void saveImage(File file){pendingFile=file.getAbsolutePath();Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("image/png");i.putExtra(Intent.EXTRA_TITLE,file.getName());try{startActivityForResult(i,10);}catch(ActivityNotFoundException e){toast("برنامهٔ مدیریت فایل در دسترس نیست");}}
    @Override public void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(request!=10||result!=RESULT_OK||data==null||data.getData()==null||pendingFile==null)return;String path=pendingFile;pendingFile=null;Uri uri=data.getData();new Thread(()->{try(InputStream in=new FileInputStream(path);OutputStream out=getContentResolver().openOutputStream(uri)){if(out==null)throw new IOException();byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);runOnUiThread(()->toast("عکس با نام شرکت ذخیره شد"));}catch(IOException e){runOnUiThread(()->toast("ذخیرهٔ عکس انجام نشد؛ دوباره تلاش کنید"));}}).start();}
    @Override public void onDestroy(){if(busyDialog!=null&&busyDialog.isShowing())busyDialog.dismiss();if(activeDialog!=null&&activeDialog.isShowing())activeDialog.dismiss();db.close();super.onDestroy();}
}
