package com.profesor.calificaciones;

import android.app.*;
import android.os.Bundle;
import android.content.*;
import android.database.Cursor;
import android.database.sqlite.*;
import android.graphics.Typeface;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends Activity {
    DB db;
    LinearLayout root, content;
    final String[] STATUS = {"Presente","Falta","Retardo","Justificada"};

    @Override public void onCreate(Bundle b){ super.onCreate(b); db=new DB(this); showHome(); }
    TextView title(String s){ TextView t=new TextView(this); t.setText(s); t.setTextSize(24); t.setTypeface(null, Typeface.BOLD); t.setPadding(24,24,24,16); return t; }
    TextView label(String s){ TextView t=new TextView(this); t.setText(s); t.setTextSize(17); t.setPadding(20,12,20,12); return t; }
    Button btn(String s, View.OnClickListener l){ Button b=new Button(this); b.setText(s); b.setOnClickListener(l); return b; }
    EditText input(String hint){ EditText e=new EditText(this); e.setHint(hint); e.setPadding(20,10,20,10); return e; }
    void base(String name){
        ScrollView sv=new ScrollView(this); root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(18,18,18,30);
        sv.addView(root); root.addView(title(name)); content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); root.addView(content);
        root.addView(btn("← Menú principal",v->showHome())); setContentView(sv);
    }
    void toast(String s){ Toast.makeText(this,s,Toast.LENGTH_SHORT).show(); }

    void showHome(){
        base("Calificaciones - versión reconstruida");
        content.addView(label("Funciones recuperadas y ampliadas para uso escolar."));
        content.addView(btn("1. Alumnos", v->showStudents()));
        content.addView(btn("2. Historial de asistencia", v->showAttendance()));
        content.addView(btn("3. Rubros y porcentajes", v->showRubrics()));
        content.addView(btn("4. Equipos y evaluación individual", v->showTeams()));
        content.addView(btn("5. Prácticas y promedio del parcial", v->showPractices()));
    }

    void showStudents(){
        base("Alumnos");
        EditText n=input("Nombre del alumno"); content.addView(n);
        content.addView(btn("Agregar alumno",v->{ String s=n.getText().toString().trim(); if(s.isEmpty())return; db.exec("INSERT INTO students(name) VALUES(?)",s); showStudents(); }));
        Cursor c=db.q("SELECT id,name FROM students ORDER BY name");
        while(c.moveToNext()){
            int id=c.getInt(0); String name=c.getString(1);
            LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
            row.addView(label(name),new LinearLayout.LayoutParams(0,-2,1));
            row.addView(btn("Eliminar",v->{db.exec("DELETE FROM students WHERE id=?",id);showStudents();}));
            content.addView(row);
        } c.close();
    }

    void showAttendance(){
        base("Historial de asistencia");
        String today=new SimpleDateFormat("yyyy-MM-dd",Locale.getDefault()).format(new Date());
        EditText d=input("Fecha YYYY-MM-DD"); d.setText(today); content.addView(d);
        content.addView(btn("Pasar lista en esta fecha",v->attendanceForDate(d.getText().toString().trim())));
        content.addView(label("Historial guardado:"));
        Cursor c=db.q("SELECT a.day,s.name,a.status FROM attendance a JOIN students s ON s.id=a.student_id ORDER BY a.day DESC,s.name");
        while(c.moveToNext()) content.addView(label(c.getString(0)+" · "+c.getString(1)+" · "+c.getString(2)));
        c.close();
    }

    void attendanceForDate(String day){
        base("Asistencia · "+day);
        Cursor c=db.q("SELECT id,name FROM students ORDER BY name");
        while(c.moveToNext()){
            int sid=c.getInt(0); String name=c.getString(1);
            LinearLayout r=new LinearLayout(this); r.setOrientation(LinearLayout.VERTICAL); r.addView(label(name));
            Spinner sp=new Spinner(this); sp.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,STATUS));
            String old=db.str("SELECT status FROM attendance WHERE student_id=? AND day=?",sid,day);
            for(int i=0;i<STATUS.length;i++) if(STATUS[i].equals(old)) sp.setSelection(i);
            r.addView(sp);
            r.addView(btn("Guardar",v->{db.exec("INSERT OR REPLACE INTO attendance(student_id,day,status) VALUES(?,?,?)",sid,day,sp.getSelectedItem().toString());toast("Guardado");}));
            content.addView(r);
        } c.close();
        content.addView(btn("Ver historial",v->showAttendance()));
    }

    void showRubrics(){
        base("Rubros y porcentajes");
        EditText n=input("Tipo de actividad, ej. Prácticas"); EditText w=input("Porcentaje, ej. 30"); w.setInputType(2|8192);
        content.addView(n); content.addView(w);
        content.addView(btn("Agregar / actualizar rubro",v->{
            try{
                String name=n.getText().toString().trim(); double p=Double.parseDouble(w.getText().toString());
                if(name.isEmpty() || p<0 || p>100){ toast("Datos inválidos"); return; }
                db.exec("INSERT OR REPLACE INTO rubrics(name,weight) VALUES(?,?)",name,p); showRubrics();
            }catch(Exception e){toast("Datos inválidos");}
        }));
        Cursor c=db.q("SELECT name,weight FROM rubrics ORDER BY name"); double total=0;
        while(c.moveToNext()){String name=c.getString(0);double p=c.getDouble(1);total+=p; content.addView(label(name+" = "+fmt(p)+"%"));}
        c.close(); content.addView(label("Total configurado: "+fmt(total)+"%"+(Math.abs(total-100)<0.01?" ✓":" (debe sumar 100%)")));
    }

    void showTeams(){
        base("Equipos y evaluación individual");
        EditText team=input("Nombre del equipo"); content.addView(team);
        content.addView(btn("Crear equipo",v->{String s=team.getText().toString().trim();if(!s.isEmpty()){db.exec("INSERT INTO teams(name) VALUES(?)",s);showTeams();}}));
        Cursor tc=db.q("SELECT id,name FROM teams ORDER BY name");
        while(tc.moveToNext()){
            int tid=tc.getInt(0);String tn=tc.getString(1); content.addView(title(tn));
            Spinner students=studentSpinner(); content.addView(students);
            content.addView(btn("Agregar alumno al equipo",v->{Object o=students.getSelectedItem(); if(o!=null){Item it=(Item)o; db.exec("INSERT OR IGNORE INTO team_members(team_id,student_id) VALUES(?,?)",tid,it.id);showTeams();}}));
            Cursor mc=db.q("SELECT s.id,s.name FROM team_members m JOIN students s ON s.id=m.student_id WHERE m.team_id=? ORDER BY s.name",tid);
            while(mc.moveToNext()){
                int sid=mc.getInt(0);String sn=mc.getString(1);
                LinearLayout r=new LinearLayout(this);r.setOrientation(LinearLayout.HORIZONTAL);
                r.addView(label(sn),new LinearLayout.LayoutParams(0,-2,1));
                EditText g=input("0-10");g.setInputType(2|8192);
                String old=db.str("SELECT grade FROM team_grades WHERE team_id=? AND student_id=?",tid,sid);g.setText(old);
                r.addView(g,new LinearLayout.LayoutParams(250,-2));
                r.addView(btn("Guardar",v->{try{double grade=Double.parseDouble(g.getText().toString()); if(grade<0||grade>10){toast("Use 0 a 10");return;} db.exec("INSERT OR REPLACE INTO team_grades(team_id,student_id,grade) VALUES(?,?,?)",tid,sid,grade);toast("Nota guardada");}catch(Exception e){toast("Nota inválida");}}));
                content.addView(r);
            }mc.close();
        }tc.close();
    }

    Spinner studentSpinner(){
        Spinner s=new Spinner(this); ArrayList<Item> a=new ArrayList<>();
        Cursor c=db.q("SELECT id,name FROM students ORDER BY name"); while(c.moveToNext())a.add(new Item(c.getInt(0),c.getString(1)));c.close();
        s.setAdapter(new ArrayAdapter<Item>(this,android.R.layout.simple_spinner_dropdown_item,a));return s;
    }

    void showPractices(){
        base("Prácticas y promedio del parcial");
        Spinner st=studentSpinner(); content.addView(st);
        Spinner rub=new Spinner(this); ArrayList<String> rs=new ArrayList<>();
        Cursor rc=db.q("SELECT name FROM rubrics ORDER BY name");while(rc.moveToNext())rs.add(rc.getString(0));rc.close();
        rub.setAdapter(new ArrayAdapter<String>(this,android.R.layout.simple_spinner_dropdown_item,rs));content.addView(rub);
        EditText act=input("Nombre de actividad, ej. Práctica 1"); EditText grade=input("Calificación 0-10"); grade.setInputType(2|8192);
        content.addView(act);content.addView(grade);
        content.addView(btn("Guardar calificación",v->{
            try{
                Item si=(Item)st.getSelectedItem();String r=(String)rub.getSelectedItem();double g=Double.parseDouble(grade.getText().toString());
                if(si==null||r==null||g<0||g>10){toast("Datos inválidos");return;}
                db.exec("INSERT INTO grades(student_id,rubric,activity,grade) VALUES(?,?,?,?)",si.id,r,act.getText().toString().trim(),g);showPractices();
            }catch(Exception e){toast("Datos inválidos");}
        }));
        content.addView(title("Promedios"));
        Cursor sc=db.q("SELECT id,name FROM students ORDER BY name");
        while(sc.moveToNext()){
            int sid=sc.getInt(0);String sn=sc.getString(1);double partial=0, used=0;StringBuilder det=new StringBuilder();
            Cursor rcur=db.q("SELECT name,weight FROM rubrics ORDER BY name");
            while(rcur.moveToNext()){
                String rn=rcur.getString(0);double wt=rcur.getDouble(1);
                Double avg=db.dbl("SELECT AVG(grade) FROM grades WHERE student_id=? AND rubric=?",sid,rn);
                if(avg!=null){partial += avg*(wt/100.0);used+=wt;det.append("\n  ").append(rn).append(": ").append(fmt(avg)).append(" × ").append(fmt(wt)).append("%");}
            }rcur.close();
            Double teamAvg=db.dbl("SELECT AVG(grade) FROM team_grades WHERE student_id=?",sid);
            if(teamAvg!=null)det.append("\n  Equipos: ").append(fmt(teamAvg));
            content.addView(label(sn+"\nPromedio parcial: "+fmt(partial)+(used<99.99?" (rubros con nota: "+fmt(used)+"%)":"")+det));
        }sc.close();
    }

    static String fmt(double d){return String.format(Locale.US,"%.2f",d);}
    static class Item{int id;String name;Item(int i,String n){id=i;name=n;}public String toString(){return name;}}

    static class DB extends SQLiteOpenHelper{
        DB(Context c){super(c,"calificaciones_reconstruida.db",null,1);}
        public void onCreate(SQLiteDatabase d){
            d.execSQL("CREATE TABLE students(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL)");
            d.execSQL("CREATE TABLE attendance(student_id INTEGER,day TEXT,status TEXT,PRIMARY KEY(student_id,day))");
            d.execSQL("CREATE TABLE rubrics(name TEXT PRIMARY KEY,weight REAL NOT NULL)");
            d.execSQL("CREATE TABLE teams(id INTEGER PRIMARY KEY AUTOINCREMENT,name TEXT NOT NULL)");
            d.execSQL("CREATE TABLE team_members(team_id INTEGER,student_id INTEGER,PRIMARY KEY(team_id,student_id))");
            d.execSQL("CREATE TABLE team_grades(team_id INTEGER,student_id INTEGER,grade REAL,PRIMARY KEY(team_id,student_id))");
            d.execSQL("CREATE TABLE grades(id INTEGER PRIMARY KEY AUTOINCREMENT,student_id INTEGER,rubric TEXT,activity TEXT,grade REAL)");
            d.execSQL("INSERT INTO rubrics(name,weight) VALUES('Prácticas',30),('Exámenes',40),('Proyecto',20),('Asistencia',10)");
        }
        public void onUpgrade(SQLiteDatabase d,int o,int n){}
        void exec(String sql,Object...a){getWritableDatabase().execSQL(sql,a);}
        Cursor q(String sql,Object...a){String[] s=new String[a.length];for(int i=0;i<a.length;i++)s[i]=String.valueOf(a[i]);return getReadableDatabase().rawQuery(sql,s);}
        String str(String sql,Object...a){Cursor c=q(sql,a);String r="";if(c.moveToFirst()&&!c.isNull(0))r=c.getString(0);c.close();return r;}
        Double dbl(String sql,Object...a){Cursor c=q(sql,a);Double r=null;if(c.moveToFirst()&&!c.isNull(0))r=c.getDouble(0);c.close();return r;}
    }
}
