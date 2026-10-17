package com.fnos.homestay;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Locale;

/**
 * 民宿记账 —— 自用，单机，不联网。
 *
 * 明细页：按日期倒序列出每一笔收入，可筛选「全部 / 本月 / 今年」，点条目可改可删。
 * 汇总页：选年份，看 12 个月各自合计 + 全年合计，点某个月直接跳到该月明细。
 * 数据存在 App 私有目录的 records.txt，一行一笔：日期|金额|备注。
 *
 * 注意：本文件不写任何匿名/内部类（d8 解析匿名内部类会崩），
 * 所有点击事件都由 Activity 自己实现 View.OnClickListener 分发。
 */
public class MainActivity extends Activity
        implements View.OnClickListener, DatePickerDialog.OnDateSetListener {

    private static final String FILE_NAME = "records.txt";

    private static final String C_BG = "#0E1216";
    private static final String C_CARD = "#171E25";
    private static final String C_TEXT = "#E8EDF2";
    private static final String C_DIM = "#8A97A4";
    private static final String C_FAINT = "#5A6875";
    private static final String C_ACCENT = "#1F6FEB";
    private static final String C_MONEY = "#4CC38A";
    private static final String C_RED = "#D9534F";

    /** 全部记录：每项 = {yyyy-MM-dd, 金额(两位小数字符串), 备注}，始终按日期倒序。 */
    private final ArrayList<String[]> recs = new ArrayList<String[]>();

    private int page = 0;          // 0 = 明细，1 = 汇总
    private int filter = 0;        // 0 = 全部，1 = 本月，2 = 今年
    private String pinMonth = null; // 非空时明细只显示这个月，如 "2026-03"
    private int year = 2026;       // 汇总页当前年份
    private int editIndex = -1;    // 正在编辑的记录下标，-1 = 新增
    private String editDate = "";  // 对话框里选中的日期

    private LinearLayout pageDetail;
    private LinearLayout pageSummary;
    private LinearLayout detailBox;
    private LinearLayout monthBox;
    private TextView sumBar;
    private TextView yearLabel;
    private Button filterBtn;
    private Button tabDetail;
    private Button tabSummary;

    private AlertDialog editDlg;
    private AlertDialog confirmDlg;
    private EditText inAmount;
    private EditText inNote;
    private TextView dlgDateView;  // 对话框里显示日期的那一栏，点它弹日历

    // ============================ 界面搭建 ============================

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Calendar now = Calendar.getInstance();
        year = now.get(Calendar.YEAR);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor(C_BG));
        root.setPadding(dp(16), dp(30), dp(16), dp(12));

        TextView title = new TextView(this);
        title.setText("民宿记账");
        title.setTextColor(Color.parseColor(C_TEXT));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 25);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        root.addView(title, matchWrap());

        TextView sub = new TextView(this);
        sub.setText("美团民宿 · 收入流水（只存本机）");
        sub.setTextColor(Color.parseColor(C_DIM));
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = matchWrap();
        subLp.topMargin = dp(6);
        subLp.bottomMargin = dp(18);
        root.addView(sub, subLp);

        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabDetail = mkBtn("明细", "tab0");
        tabSummary = mkBtn("汇总", "tab1");
        LinearLayout.LayoutParams t1 = new LinearLayout.LayoutParams(0, dp(46), 1f);
        t1.rightMargin = dp(5);
        tabs.addView(tabDetail, t1);
        LinearLayout.LayoutParams t2 = new LinearLayout.LayoutParams(0, dp(46), 1f);
        t2.leftMargin = dp(5);
        tabs.addView(tabSummary, t2);
        LinearLayout.LayoutParams tabsLp = matchWrap();
        tabsLp.bottomMargin = dp(16);
        root.addView(tabs, tabsLp);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        pageDetail = buildDetailPage();
        pageSummary = buildSummaryPage();
        content.addView(pageDetail, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        content.addView(pageSummary, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);

        load();
        switchPage(0);
        buildList();
        buildMonths();
    }

    /** 明细页：合计条 + 列表 + 底部「记一笔」。 */
    private LinearLayout buildDetailPage() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);

        sumBar = new TextView(this);
        sumBar.setTextColor(Color.parseColor(C_TEXT));
        sumBar.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        sumBar.setTypeface(Typeface.DEFAULT_BOLD);
        bar.addView(sumBar, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        filterBtn = mkBtn("全部 ▾", "filter");
        filterBtn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        filterBtn.setBackgroundColor(Color.parseColor(C_CARD));
        filterBtn.setTextColor(Color.parseColor(C_DIM));
        filterBtn.setPadding(dp(12), 0, dp(12), 0);
        bar.addView(filterBtn, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)));
        box.addView(bar, matchWrap());

        detailBox = new LinearLayout(this);
        detailBox.setOrientation(LinearLayout.VERTICAL);

        ScrollView sc = new ScrollView(this);
        sc.setVerticalScrollBarEnabled(false);
        sc.addView(detailBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scLp.topMargin = dp(10);
        box.addView(sc, scLp);

        Button add = mkBtn("+  记一笔", "add");
        add.setBackgroundColor(Color.parseColor(C_ACCENT));
        add.setTextColor(Color.WHITE);
        add.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        addLp.topMargin = dp(10);
        box.addView(add, addLp);

        return box;
    }

    /** 汇总页：年份切换 + 12 个月合计 + 全年合计。 */
    private LinearLayout buildSummaryPage() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);

        LinearLayout yr = new LinearLayout(this);
        yr.setOrientation(LinearLayout.HORIZONTAL);
        yr.setGravity(Gravity.CENTER_VERTICAL);

        Button prev = mkBtn("◀", "yprev");
        prev.setTextColor(Color.parseColor(C_TEXT));
        prev.setBackgroundColor(Color.parseColor(C_CARD));
        yr.addView(prev, new LinearLayout.LayoutParams(dp(48), dp(44)));

        yearLabel = new TextView(this);
        yearLabel.setTextColor(Color.parseColor(C_TEXT));
        yearLabel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        yearLabel.setTypeface(Typeface.DEFAULT_BOLD);
        yearLabel.setGravity(Gravity.CENTER);
        yr.addView(yearLabel, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        Button next = mkBtn("▶", "ynext");
        next.setTextColor(Color.parseColor(C_TEXT));
        next.setBackgroundColor(Color.parseColor(C_CARD));
        yr.addView(next, new LinearLayout.LayoutParams(dp(48), dp(44)));
        box.addView(yr, matchWrap());

        monthBox = new LinearLayout(this);
        monthBox.setOrientation(LinearLayout.VERTICAL);

        ScrollView sc = new ScrollView(this);
        sc.setVerticalScrollBarEnabled(false);
        sc.addView(monthBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams scLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scLp.topMargin = dp(12);
        box.addView(sc, scLp);

        TextView tip = new TextView(this);
        tip.setText("点某个月 → 直接看那个月的明细");
        tip.setTextColor(Color.parseColor(C_FAINT));
        tip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tip.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tipLp = matchWrap();
        tipLp.topMargin = dp(10);
        box.addView(tip, tipLp);

        return box;
    }

    // ============================ 明细列表 ============================

    private void buildList() {
        detailBox.removeAllViews();
        String pre = rangePrefix();
        double sum = 0;
        int cnt = 0;
        for (int i = 0; i < recs.size(); i++) {
            String[] r = recs.get(i);
            if (pre.length() > 0 && !r[0].startsWith(pre)) {
                continue;
            }
            sum += amt(r[1]);
            cnt++;
            detailBox.addView(mkRecRow(i, r), rowLp(cnt));
        }
        sumBar.setText("合计 " + yuan(sum) + " · " + cnt + " 笔");
        filterBtn.setText(filterName());
        if (cnt == 0) {
            detailBox.addView(mkEmptyHint(), rowLp(1));
        }
    }

    private View mkRecRow(int index, String[] r) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setBackgroundResource(R.drawable.row_bg);
        row.setTag("rec:" + index);
        row.setOnClickListener(this);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);

        TextView day = new TextView(this);
        day.setText(weekday(r[0]) + "  " + shortDate(r[0]));
        day.setTextColor(Color.parseColor(C_TEXT));
        day.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        day.setTypeface(Typeface.DEFAULT_BOLD);
        col.addView(day, matchWrap());

        TextView note = new TextView(this);
        note.setText(r[2].length() == 0 ? "（无备注）" : r[2]);
        note.setTextColor(Color.parseColor(C_DIM));
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        LinearLayout.LayoutParams noteLp = matchWrap();
        noteLp.topMargin = dp(3);
        col.addView(note, noteLp);
        row.addView(col, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView money = new TextView(this);
        money.setText(yuan(amt(r[1])));
        money.setTextColor(Color.parseColor(C_MONEY));
        money.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        money.setTypeface(Typeface.DEFAULT_BOLD);
        LinearLayout.LayoutParams moneyLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        moneyLp.leftMargin = dp(10);
        row.addView(money, moneyLp);

        return row;
    }

    private View mkEmptyHint() {
        TextView t = new TextView(this);
        t.setText("这个范围还没有记录。\n\n点下面「+ 记一笔」开始记账 —— 日期默认今天，"
                + "点日期可以改成过去某天补记。\n\n点任意一条记录可以修改金额、改日期或删除。");
        t.setTextColor(Color.parseColor(C_DIM));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        t.setLineSpacing(dp(4), 1f);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(10), dp(40), dp(10), dp(10));
        return t;
    }

    // ============================ 月 / 年汇总 ============================

    private void buildMonths() {
        monthBox.removeAllViews();
        yearLabel.setText(year + " 年");

        double total = 0;
        int totalCnt = 0;
        for (int m = 1; m <= 12; m++) {
            String pre = String.format(Locale.US, "%04d-%02d", year, m);
            double s = 0;
            int c = 0;
            for (int i = 0; i < recs.size(); i++) {
                String[] r = recs.get(i);
                if (r[0].startsWith(pre)) {
                    s += amt(r[1]);
                    c++;
                }
            }
            total += s;
            totalCnt += c;
            monthBox.addView(mkMonthRow(m, pre, s, c), rowLp(m));
        }
        monthBox.addView(mkTotalRow(total, totalCnt), rowLp(0));
    }

    private View mkMonthRow(int month, String pre, double sum, int cnt) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setBackgroundResource(R.drawable.row_bg);
        row.setTag("mon:" + pre);
        row.setOnClickListener(this);

        TextView name = new TextView(this);
        name.setText(month + " 月");
        name.setTextColor(Color.parseColor(C_TEXT));
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        if (cnt > 0) {
            name.setTypeface(Typeface.DEFAULT_BOLD);
        }
        row.addView(name, new LinearLayout.LayoutParams(
                dp(58), ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView c = new TextView(this);
        c.setText(cnt > 0 ? (cnt + " 笔") : "—");
        c.setTextColor(Color.parseColor(C_FAINT));
        c.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        row.addView(c, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView money = new TextView(this);
        money.setText(cnt > 0 ? yuan(sum) : "");
        money.setTextColor(Color.parseColor(cnt > 0 ? C_MONEY : C_FAINT));
        money.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        if (cnt > 0) {
            money.setTypeface(Typeface.DEFAULT_BOLD);
        }
        row.addView(money, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        return row;
    }

    private View mkTotalRow(double sum, int cnt) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(14), dp(14), dp(14), dp(14));
        row.setBackgroundColor(Color.parseColor(C_CARD));

        TextView name = new TextView(this);
        name.setText("全年合计");
        name.setTextColor(Color.parseColor(C_TEXT));
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(name, new LinearLayout.LayoutParams(
                dp(78), ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView c = new TextView(this);
        c.setText(cnt + " 笔");
        c.setTextColor(Color.parseColor(C_DIM));
        c.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        row.addView(c, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView money = new TextView(this);
        money.setText(yuan(sum));
        money.setTextColor(Color.parseColor(C_MONEY));
        money.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        money.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(money, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        return row;
    }

    // ============================ 记 / 改 / 删 ============================

    private void openDialog(int index) {
        editIndex = index;
        if (index >= 0 && index < recs.size()) {
            editDate = recs.get(index)[0];
        } else {
            editDate = today();
        }

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(10), dp(20), dp(6));
        body.setMinimumWidth(dp(280));

        TextView dl = new TextView(this);
        dl.setText("日期");
        dl.setTextColor(Color.parseColor(C_DIM));
        dl.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);

        dlgDateView = new TextView(this);
        dlgDateView.setText(pretty(editDate) + "   ▾");
        dlgDateView.setTextColor(Color.parseColor(C_TEXT));
        dlgDateView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        dlgDateView.setPadding(dp(14), dp(11), dp(14), dp(11));
        dlgDateView.setBackgroundColor(Color.parseColor(C_CARD));
        dlgDateView.setTag("pickdate");
        dlgDateView.setOnClickListener(this);

        LinearLayout dateRow = new LinearLayout(this);
        dateRow.setOrientation(LinearLayout.HORIZONTAL);
        dateRow.setGravity(Gravity.CENTER_VERTICAL);
        dateRow.addView(dl, new LinearLayout.LayoutParams(
                dp(50), ViewGroup.LayoutParams.WRAP_CONTENT));
        dateRow.addView(dlgDateView, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        body.addView(dateRow, matchWrap());

        TextView l2 = new TextView(this);
        l2.setText("收入金额（元）");
        l2.setTextColor(Color.parseColor(C_DIM));
        l2.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        LinearLayout.LayoutParams l2Lp = matchWrap();
        l2Lp.topMargin = dp(16);
        body.addView(l2, l2Lp);

        inAmount = new EditText(this);
        inAmount.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        inAmount.setHint("例如 280");
        inAmount.setHintTextColor(Color.parseColor(C_FAINT));
        inAmount.setTextColor(Color.parseColor(C_TEXT));
        inAmount.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        if (index >= 0 && index < recs.size()) {
            inAmount.setText(trimZero(recs.get(index)[1]));
        }
        body.addView(inAmount, matchWrap());

        TextView l3 = new TextView(this);
        l3.setText("备注（房型 / 平台，可留空）");
        l3.setTextColor(Color.parseColor(C_DIM));
        l3.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        LinearLayout.LayoutParams l3Lp = matchWrap();
        l3Lp.topMargin = dp(14);
        body.addView(l3, l3Lp);

        inNote = new EditText(this);
        inNote.setHint("例如 3 号房 · 美团");
        inNote.setHintTextColor(Color.parseColor(C_FAINT));
        inNote.setTextColor(Color.parseColor(C_TEXT));
        inNote.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        if (index >= 0 && index < recs.size()) {
            inNote.setText(recs.get(index)[2]);
        }
        body.addView(inNote, matchWrap());

        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams btnsLp = matchWrap();
        btnsLp.topMargin = dp(20);

        if (index >= 0) {
            Button del = mkBtn("删除", "del");
            del.setTextColor(Color.parseColor(C_RED));
            del.setBackgroundColor(Color.parseColor(C_CARD));
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(0, dp(46), 1f);
            dlp.rightMargin = dp(5);
            btns.addView(del, dlp);
        }

        Button cancel = mkBtn("取消", "cancel");
        cancel.setTextColor(Color.parseColor(C_TEXT));
        cancel.setBackgroundColor(Color.parseColor(C_CARD));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        clp.leftMargin = dp(5);
        clp.rightMargin = dp(5);
        btns.addView(cancel, clp);

        Button save = mkBtn("保存", "save");
        save.setTextColor(Color.WHITE);
        save.setBackgroundColor(Color.parseColor(C_ACCENT));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        slp.leftMargin = dp(5);
        btns.addView(save, slp);
        body.addView(btns, btnsLp);

        editDlg = new AlertDialog.Builder(this)
                .setTitle(index >= 0 ? "编辑记录" : "记一笔收入")
                .setView(body)
                .create();
        editDlg.show();
    }

    private void saveFromDialog() {
        String raw = inAmount.getText().toString().trim();
        String clean = raw.replace("¥", "").replace(",", "").replace("，", "")
                .replace(" ", "").replace("元", "");
        if (clean.length() == 0) {
            toast("金额还没填");
            return;
        }
        double a;
        try {
            a = Double.parseDouble(clean);
        } catch (Exception e) {
            toast("金额只能是数字，例如 280 或 280.5");
            return;
        }
        if (a < 0) {
            toast("金额不能是负数");
            return;
        }
        String note = inNote.getText().toString().trim()
                .replace("|", " ").replace("\n", " ").replace("\r", " ");
        String[] rec = { editDate, String.format(Locale.US, "%.2f", a), note };
        if (editIndex >= 0 && editIndex < recs.size()) {
            recs.set(editIndex, rec);
            toast("已保存修改");
        } else {
            recs.add(rec);
            toast("已记 " + yuan(a) + "（" + pretty(editDate) + "）");
        }
        sortRecs();
        save();
        if (editDlg != null) {
            editDlg.dismiss();
        }
        buildList();
        buildMonths();
    }

    private void confirmDelete() {
        if (editIndex < 0 || editIndex >= recs.size()) {
            return;
        }
        String[] r = recs.get(editIndex);

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(20), dp(10), dp(20), dp(6));
        body.setMinimumWidth(dp(260));

        TextView t = new TextView(this);
        t.setText("删掉这条？\n" + pretty(r[0]) + "   " + yuan(amt(r[1]))
                + (r[2].length() > 0 ? ("\n" + r[2]) : ""));
        t.setTextColor(Color.parseColor(C_TEXT));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        body.addView(t, matchWrap());

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams btnRowLp = matchWrap();
        btnRowLp.topMargin = dp(20);

        Button no = mkBtn("取消", "cancelonly");
        no.setTextColor(Color.parseColor(C_TEXT));
        no.setBackgroundColor(Color.parseColor(C_CARD));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        nlp.rightMargin = dp(5);
        row.addView(no, nlp);

        Button yes = mkBtn("删除", "delyes");
        yes.setTextColor(Color.WHITE);
        yes.setBackgroundColor(Color.parseColor(C_RED));
        LinearLayout.LayoutParams ylp = new LinearLayout.LayoutParams(0, dp(46), 1f);
        ylp.leftMargin = dp(5);
        row.addView(yes, ylp);
        body.addView(row, btnRowLp);

        confirmDlg = new AlertDialog.Builder(this)
                .setTitle("删除记录")
                .setView(body)
                .create();
        confirmDlg.show();
    }

    private void doDelete() {
        if (editIndex >= 0 && editIndex < recs.size()) {
            recs.remove(editIndex);
        }
        editIndex = -1;
        save();
        if (confirmDlg != null) {
            confirmDlg.dismiss();
        }
        if (editDlg != null) {
            editDlg.dismiss();
        }
        buildList();
        buildMonths();
        toast("已删除");
    }

    private void pickDate() {
        Calendar c = Calendar.getInstance();
        String[] p = editDate.split("-");
        if (p.length == 3) {
            c.set(parseInt(p[0], c.get(Calendar.YEAR)),
                    parseInt(p[1], c.get(Calendar.MONTH) + 1) - 1,
                    parseInt(p[2], c.get(Calendar.DAY_OF_MONTH)));
        }
        new DatePickerDialog(this, this,
                c.get(Calendar.YEAR), c.get(Calendar.MONTH),
                c.get(Calendar.DAY_OF_MONTH)).show();
    }

    @Override
    public void onDateSet(DatePicker view, int y, int m, int d) {
        editDate = iso(y, m + 1, d);
        if (dlgDateView != null) {
            dlgDateView.setText(pretty(editDate) + "   ▾");
        }
    }

    // ============================ 点击分发 ============================

    @Override
    public void onClick(View v) {
        Object tagObj = v.getTag();
        String tag = (tagObj instanceof String) ? (String) tagObj : "";

        if (tag.startsWith("rec:")) {
            openDialog(parseInt(tag.substring(4), -1));
        } else if (tag.startsWith("mon:")) {
            pinMonth = tag.substring(4);
            filter = 0;
            switchPage(0);
            buildList();
        } else if ("tab0".equals(tag)) {
            switchPage(0);
        } else if ("tab1".equals(tag)) {
            switchPage(1);
            buildMonths();
        } else if ("filter".equals(tag)) {
            if (pinMonth != null) {
                pinMonth = null;
                filter = 0;
            } else {
                filter = (filter + 1) % 3;
            }
            buildList();
        } else if ("yprev".equals(tag)) {
            if (year > 2000) {
                year--;
                buildMonths();
            }
        } else if ("ynext".equals(tag)) {
            if (year < 2100) {
                year++;
                buildMonths();
            }
        } else if ("add".equals(tag)) {
            openDialog(-1);
        } else if ("pickdate".equals(tag)) {
            pickDate();
        } else if ("save".equals(tag)) {
            saveFromDialog();
        } else if ("cancel".equals(tag)) {
            if (editDlg != null) {
                editDlg.dismiss();
            }
        } else if ("del".equals(tag)) {
            confirmDelete();
        } else if ("cancelonly".equals(tag)) {
            if (confirmDlg != null) {
                confirmDlg.dismiss();
            }
        } else if ("delyes".equals(tag)) {
            doDelete();
        }
    }

    private void switchPage(int p) {
        page = p;
        pageDetail.setVisibility(p == 0 ? View.VISIBLE : View.GONE);
        pageSummary.setVisibility(p == 1 ? View.VISIBLE : View.GONE);
        paintTab(tabDetail, p == 0);
        paintTab(tabSummary, p == 1);
    }

    private void paintTab(Button b, boolean on) {
        b.setBackgroundColor(Color.parseColor(on ? C_ACCENT : C_CARD));
        b.setTextColor(Color.parseColor(on ? "#FFFFFF" : C_DIM));
    }

    // ============================ 存储 ============================

    private void load() {
        recs.clear();
        File f = new File(getFilesDir(), FILE_NAME);
        if (!f.exists()) {
            return;
        }
        BufferedReader br = null;
        try {
            br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.length() == 0) {
                    continue;
                }
                String[] p = line.split("\\|", -1);
                if (p.length < 2 || p[0].length() < 8) {
                    continue;
                }
                double v;
                try {
                    v = Double.parseDouble(p[1]);
                } catch (Exception e) {
                    continue;
                }
                recs.add(new String[]{
                        p[0],
                        String.format(Locale.US, "%.2f", v),
                        p.length > 2 ? p[2] : ""
                });
            }
        } catch (Exception e) {
            toast("读取账本失败：" + e.getMessage());
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (Exception ignored) {
                }
            }
        }
        sortRecs();
    }

    private void save() {
        OutputStream os = null;
        OutputStreamWriter w = null;
        try {
            File f = new File(getFilesDir(), FILE_NAME);
            os = new java.io.FileOutputStream(f);
            w = new OutputStreamWriter(os, "UTF-8");
            for (int i = 0; i < recs.size(); i++) {
                String[] r = recs.get(i);
                w.write(r[0] + "|" + r[1] + "|" + r[2] + "\n");
            }
            w.flush();
        } catch (Exception e) {
            toast("保存失败：" + e.getMessage());
        } finally {
            if (w != null) {
                try {
                    w.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    /** 按日期倒序（同一天保持录入顺序）—— 手写插入排序，避免匿名 Comparator。 */
    private void sortRecs() {
        for (int i = 1; i < recs.size(); i++) {
            String[] cur = recs.get(i);
            int j = i - 1;
            while (j >= 0 && recs.get(j)[0].compareTo(cur[0]) < 0) {
                recs.set(j + 1, recs.get(j));
                j--;
            }
            recs.set(j + 1, cur);
        }
    }

    // ============================ 小工具 ============================

    private String rangePrefix() {
        if (pinMonth != null) {
            return pinMonth;
        }
        Calendar c = Calendar.getInstance();
        if (filter == 1) {
            return String.format(Locale.US, "%04d-%02d",
                    c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1);
        }
        if (filter == 2) {
            return String.valueOf(c.get(Calendar.YEAR));
        }
        return "";
    }

    private String filterName() {
        if (pinMonth != null) {
            return pinMonth.replace("-", "/") + "  ✕";
        }
        if (filter == 1) {
            return "本月 ▾";
        }
        if (filter == 2) {
            return "今年 ▾";
        }
        return "全部 ▾";
    }

    private static double amt(String s) {
        try {
            return Double.parseDouble(s);
        } catch (Exception e) {
            return 0;
        }
    }

    private static String yuan(double v) {
        return "¥" + String.format(Locale.US, "%,.2f", v);
    }

    private static String trimZero(String a) {
        if (a.endsWith(".00")) {
            return a.substring(0, a.length() - 3);
        }
        return a;
    }

    private static String iso(int y, int m, int d) {
        return String.format(Locale.US, "%04d-%02d-%02d", y, m, d);
    }

    private static String today() {
        Calendar c = Calendar.getInstance();
        return iso(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1,
                c.get(Calendar.DAY_OF_MONTH));
    }

    private static String pretty(String isoDate) {
        String[] p = isoDate.split("-");
        if (p.length != 3) {
            return isoDate;
        }
        return p[0] + " 年 " + parseInt(p[1], 0) + " 月 " + parseInt(p[2], 0) + " 日";
    }

    /** 当年只显示 月-日，跨年显示 年-月-日（后两位年份）。 */
    private static String shortDate(String isoDate) {
        String[] p = isoDate.split("-");
        if (p.length != 3) {
            return isoDate;
        }
        String thisYear = String.valueOf(Calendar.getInstance().get(Calendar.YEAR));
        if (p[0].equals(thisYear)) {
            return p[1] + "-" + p[2];
        }
        return p[0].substring(2) + " 年 " + parseInt(p[1], 0) + " 月 " + parseInt(p[2], 0) + " 日";
    }

    private static String weekday(String isoDate) {
        String[] p = isoDate.split("-");
        if (p.length != 3) {
            return "";
        }
        Calendar c = Calendar.getInstance();
        c.set(parseInt(p[0], 1970), parseInt(p[1], 1) - 1, parseInt(p[2], 1));
        String[] names = {"周日", "周一", "周二", "周三", "周四", "周五", "周六"};
        return names[c.get(Calendar.DAY_OF_WEEK) - 1];
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams rowLp(int index) {
        LinearLayout.LayoutParams lp = matchWrap();
        if (index > 1) {
            lp.topMargin = dp(8);
        }
        return lp;
    }

    private Button mkBtn(String text, String tag) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setAllCaps(false);
        b.setGravity(Gravity.CENTER);
        b.setTag(tag);
        b.setOnClickListener(this);
        return b;
    }
}
