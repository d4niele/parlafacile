package it.parlafacile;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.vosk.Model;
import org.vosk.android.StorageService;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Locale;

/**
 * PARLA FACILE — sottotitoli dal vivo per chi ci sente poco.
 *
 *  - Tastone verde: il tablet ascolta chi parla e scrive a caratteri enormi.
 *  - Anche i tasti del volume (o un pulsante Bluetooth) accendono/spengono l'ascolto.
 *  - Comandi vocali: "cancella", "più grande", "più piccolo".
 *  - Funziona senza internet (Vosk); con la rete usa il riconoscimento di sistema,
 *    più preciso, e se la rete cade torna da solo a quello locale.
 *  - Menu tecnico nascosto: 5 tocchi rapidi sulla barra della batteria.
 */
public class MainActivity extends Activity implements MotoreAscolto.Ascoltatore {

    // Colori ad alto contrasto
    private static final int SFONDO  = Color.parseColor("#FFFDF5");
    private static final int VERDE   = Color.parseColor("#1B7F3B");
    private static final int ROSSO   = Color.parseColor("#B3261E");
    private static final int ARANCIO = Color.parseColor("#C25E00");
    private static final int BLU     = Color.parseColor("#0D47A1");
    private static final int GRIGIO  = Color.parseColor("#757575");
    private static final int GRIGIO_SCURO = Color.parseColor("#37474F");

    private static final int MAX_RIGHE = 40;
    private static final long SPEGNI_DOPO_SILENZIO_MS = 10 * 60 * 1000L;  // 10 minuti
    private static final int RICHIESTA_MICROFONO = 1;

    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());

    // Ascolto
    private Model modello;
    private boolean modelloInCaricamento = true;
    private MotoreVosk motoreVosk;
    private MotoreRete motoreRete;
    private MotoreWhisper motoreWhisper;
    private Correttore correttore;
    private MotoreAscolto motore;
    private boolean ascolto = false;
    private long ultimaVoce = 0;
    private boolean inCarica = false;

    // Sottotitoli
    private final ArrayList<String> righe = new ArrayList<String>();
    private String parziale = "";

    // Interfaccia
    private TextView batteria, stato, sottotitoli;
    private ScrollView scorri;
    private Button tastone;

    private int tocchiSegreti = 0;
    private long primoTocco = 0;

    // ================================================================== ciclo di vita

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("parlafacile", MODE_PRIVATE);

        costruisciInterfaccia();
        motoreRete = new MotoreRete(this, this);
        caricaModelloLocale();
        chiediMicrofono();
        preparaChiosco();
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerReceiver(ricevitoreBatteria, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        avviaChioscoSePossibile();
        handler.postDelayed(controlloSilenzio, 30000);
        aggiorna();
    }

    @Override
    protected void onPause() {
        super.onPause();
        try { unregisterReceiver(ricevitoreBatteria); } catch (Exception ignored) { }
        handler.removeCallbacks(controlloSilenzio);
    }

    @Override
    protected void onDestroy() {
        fermaAscolto();
        if (motoreVosk != null) motoreVosk.chiudi();
        if (motoreRete != null) motoreRete.chiudi();
        if (motoreWhisper != null) motoreWhisper.chiudi();
        if (correttore != null) correttore.chiudi();
        if (modello != null) modello.close();
        super.onDestroy();
    }

    /** Il tasto Indietro non fa uscire dall'app. */
    @Override
    public void onBackPressed() { }

    // ================================================================== tasti fisici = tastone

    private boolean tastoTastone(int codice) {
        return codice == KeyEvent.KEYCODE_VOLUME_UP
                || codice == KeyEvent.KEYCODE_VOLUME_DOWN
                || codice == KeyEvent.KEYCODE_HEADSETHOOK          // pulsanti Bluetooth/cuffie
                || codice == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
    }

    @Override
    public boolean onKeyDown(int codice, KeyEvent evento) {
        if (tastoTastone(codice)) {
            if (evento.getRepeatCount() == 0) premiTastone();
            return true;
        }
        return super.onKeyDown(codice, evento);
    }

    @Override
    public boolean onKeyUp(int codice, KeyEvent evento) {
        if (tastoTastone(codice)) return true;
        return super.onKeyUp(codice, evento);
    }

    // ================================================================== interfaccia

    private void costruisciInterfaccia() {
        FrameLayout radice = new FrameLayout(this);
        radice.setBackgroundColor(SFONDO);

        LinearLayout principale = new LinearLayout(this);
        principale.setOrientation(LinearLayout.VERTICAL);
        int m = dp(10);
        principale.setPadding(m, m, m, m);

        // --- Batteria (e accesso nascosto al menu tecnico)
        batteria = etichetta(28, Color.WHITE, true);
        batteria.setPadding(dp(8), dp(8), dp(8), dp(8));
        batteria.setBackground(sfondo(GRIGIO));
        batteria.setText("Batteria …");
        batteria.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { toccoSegreto(); }
        });
        principale.addView(batteria, larghezzaPiena());

        // --- Stato (tipo di ascolto)
        stato = etichetta(18, GRIGIO, false);
        stato.setPadding(0, dp(4), 0, dp(4));
        principale.addView(stato, larghezzaPiena());

        // --- Sottotitoli
        scorri = new ScrollView(this);
        GradientDrawable bordo = sfondo(Color.WHITE);
        bordo.setStroke(dp(3), GRIGIO_SCURO);
        scorri.setBackground(bordo);
        sottotitoli = new TextView(this);
        sottotitoli.setTextColor(Color.BLACK);
        sottotitoli.setTypeface(Typeface.DEFAULT_BOLD);
        sottotitoli.setPadding(dp(16), dp(12), dp(16), dp(12));
        sottotitoli.setLineSpacing(0, 1.05f);
        applicaDimensione();
        scorri.addView(sottotitoli, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams lpScorri = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        lpScorri.bottomMargin = dp(10);
        principale.addView(scorri, lpScorri);

        // --- Riga comandi piccoli
        LinearLayout riga = new LinearLayout(this);
        riga.setOrientation(LinearLayout.HORIZONTAL);
        Button meno = pulsante("A–", GRIGIO_SCURO, 30);
        Button piu = pulsante("A+", GRIGIO_SCURO, 30);
        Button cancella = pulsante("CANCELLA", ARANCIO, 24);
        meno.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { cambiaDimensione(-8); }
        });
        piu.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { cambiaDimensione(+8); }
        });
        cancella.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { cancellaTutto(); }
        });
        riga.addView(meno, peso(1f, 80, true));
        riga.addView(piu, peso(1f, 80, true));
        riga.addView(cancella, peso(2f, 80, false));
        LinearLayout.LayoutParams lpRiga = larghezzaPiena();
        lpRiga.bottomMargin = dp(10);
        principale.addView(riga, lpRiga);

        // --- TASTONE
        tastone = pulsante("", GRIGIO, 40);
        tastone.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { premiTastone(); }
        });
        principale.addView(tastone, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(190)));

        radice.addView(principale, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        setContentView(radice);
    }

    private TextView etichetta(int sp, int colore, boolean grassetto) {
        TextView t = new TextView(this);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(colore);
        t.setGravity(Gravity.CENTER);
        if (grassetto) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private Button pulsante(String testo, int colore, int sp) {
        Button b = new Button(this);
        b.setText(testo);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setTextColor(Color.WHITE);
        b.setAllCaps(true);
        b.setBackground(sfondo(colore));
        return b;
    }

    private GradientDrawable sfondo(int colore) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(colore);
        g.setCornerRadius(dp(16));
        return g;
    }

    private LinearLayout.LayoutParams larghezzaPiena() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams peso(float p, int altezzaDp, boolean margineDestro) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(altezzaDp), p);
        if (margineDestro) lp.rightMargin = dp(8);
        return lp;
    }

    private int dp(int v) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics());
    }

    // ================================================================== sottotitoli

    private void applicaDimensione() {
        sottotitoli.setTextSize(TypedValue.COMPLEX_UNIT_SP, prefs.getInt("dimensione", 52));
    }

    private void cambiaDimensione(int delta) {
        int d = Math.max(28, Math.min(104, prefs.getInt("dimensione", 52) + delta));
        prefs.edit().putInt("dimensione", d).apply();
        applicaDimensione();
        scorriInFondo();
    }

    private void cancellaTutto() {
        righe.clear();
        parziale = "";
        aggiorna();
    }

    private void mostraSottotitoli() {
        if (righe.isEmpty() && parziale.isEmpty()) {
            SpannableStringBuilder s = new SpannableStringBuilder(ascolto
                    ? "Sto ascoltando…\nParlate vicino al tablet."
                    : "Premi il tasto verde\ne parla vicino al tablet.");
            s.setSpan(new ForegroundColorSpan(GRIGIO), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            s.setSpan(new RelativeSizeSpan(0.7f), 0, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sottotitoli.setText(s);
            return;
        }
        SpannableStringBuilder s = new SpannableStringBuilder();
        int n = righe.size();
        for (int i = 0; i < n; i++) {
            int inizio = s.length();
            s.append(maiuscola(righe.get(i))).append('\n');
            // Le frasi vecchie diventano piccole e grigie, le ultime due restano grandi
            if (i < n - 2) {
                s.setSpan(new ForegroundColorSpan(GRIGIO), inizio, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                s.setSpan(new RelativeSizeSpan(0.55f), inizio, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        if (!parziale.isEmpty()) {
            int inizio = s.length();
            s.append(maiuscola(parziale)).append(" …");
            s.setSpan(new ForegroundColorSpan(BLU), inizio, s.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        sottotitoli.setText(s);
        scorriInFondo();
    }

    private void scorriInFondo() {
        scorri.post(new Runnable() {
            @Override public void run() { scorri.fullScroll(View.FOCUS_DOWN); }
        });
    }

    private static String maiuscola(String t) {
        if (t.isEmpty()) return t;
        return t.substring(0, 1).toUpperCase(Locale.ITALIAN) + t.substring(1);
    }

    // ================================================================== ascolto

    private void caricaModelloLocale() {
        StorageService.unpack(this, "model-it", "model",
                new StorageService.Callback<Model>() {
                    @Override public void onComplete(Model m) {
                        modello = m;
                        modelloInCaricamento = false;
                        motoreVosk = new MotoreVosk(m, MainActivity.this);
                        if (motoreWhisper != null && prefs.getBoolean("ibrido", true)) motoreWhisper.impostaVosk(m);
                        aggiorna();
                    }
                },
                new StorageService.Callback<java.io.IOException>() {
                    @Override public void onComplete(java.io.IOException e) {
                        modelloInCaricamento = false;
                        avviso("Modello vocale locale mancante: funziona solo con la rete.");
                        aggiorna();
                    }
                });
    }

    private void chiediMicrofono() {
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{ Manifest.permission.RECORD_AUDIO }, RICHIESTA_MICROFONO);
        }
    }

    private boolean microfonoConsentito() {
        return Build.VERSION.SDK_INT < 23
                || checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressWarnings("deprecation")
    private boolean reteDisponibile() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        NetworkInfo ni = cm.getActiveNetworkInfo();
        return ni != null && ni.isConnected();
    }

    private String modo() { return prefs.getString("modo", "auto"); }

    private String indirizzoWhisper() { return prefs.getString("whisper", ""); }

    /**
     * Sceglie il motore. Automatico: server Whisper di casa (se configurato),
     * poi riconoscimento di sistema con internet, poi Vosk locale.
     */
    private MotoreAscolto scegliMotore() {
        String modo = modo();
        boolean reteOk = reteDisponibile();
        boolean sistemaOk = MotoreRete.disponibile(this);
        String url = MotoreWhisper.urlCompleto(indirizzoWhisper());

        if (modo.equals("whisper")) return creaWhisper(url);
        if (modo.equals("rete")) return sistemaOk ? motoreRete : null;
        if (modo.equals("auto")) {
            if (reteOk && !url.isEmpty()) return creaWhisper(url);
            if (reteOk && sistemaOk) return motoreRete;
        }
        if (motoreVosk != null) return motoreVosk;
        if (modo.equals("auto") && sistemaOk) return motoreRete;
        return null;
    }

    private MotoreAscolto creaWhisper(String url) {
        if (url.isEmpty()) return null;
        if (motoreWhisper != null) motoreWhisper.chiudi();
        motoreWhisper = new MotoreWhisper(url, prefs.getString("whisper_chiave", ""),
                prefs.getString("whisper_modello", ""),
                prefs.getBoolean("ibrido", true) ? modello : null, this);
        return motoreWhisper;
    }

    private void premiTastone() {
        if (ascolto) fermaAscolto();
        else avviaAscolto();
    }

    private void avviaAscolto() {
        if (!microfonoConsentito()) { chiediMicrofono(); return; }
        motore = scegliMotore();
        if (motore == null) {
            avviso(modelloInCaricamento ? "Un attimo, sto preparando l'ascolto…"
                                        : "Nessun riconoscimento vocale disponibile.");
            return;
        }
        ascolto = true;
        ultimaVoce = System.currentTimeMillis();
        motore.avvia();
        aggiorna();
    }

    private void fermaAscolto() {
        ascolto = false;
        if (motore != null) motore.ferma();
        parziale = "";
        aggiorna();
    }

    @Override
    public void parziale(String testo) {
        if (!ascolto) return;
        ultimaVoce = System.currentTimeMillis();
        parziale = testo;
        mostraSottotitoli();
    }

    @Override
    public void finale(String testo) {
        if (!ascolto) return;
        ultimaVoce = System.currentTimeMillis();
        parziale = "";
        if (!eseguiComando(testo)) {
            righe.add(testo);
            while (righe.size() > MAX_RIGHE) righe.remove(0);
            correggiConAi(testo);
        }
        mostraSottotitoli();
    }

    @Override
    public void errore(String messaggio, boolean recuperabile) {
        if (recuperabile || !ascolto) return;
        // La rete è caduta: si passa da soli all'ascolto locale
        boolean esclusivo = modo().equals("rete") || modo().equals("whisper");
        if (motore != motoreVosk && motoreVosk != null && !esclusivo) {
            avviso(motore == motoreWhisper ? "Server Whisper non raggiungibile: continuo senza internet."
                                           : "Rete assente: continuo senza internet.");
            motore = motoreVosk;
            motore.avvia();
            aggiorna();
            return;
        }
        avviso(messaggio);
        fermaAscolto();
    }

    /** Se c'è un modello di linguaggio configurato, sostituisce la riga con la versione corretta. */
    private void correggiConAi(String testo) {
        String indirizzo = prefs.getString("llm", "");
        if (indirizzo.isEmpty()) return;
        if (correttore == null) correttore = new Correttore(indirizzo, prefs.getString("llm_modello", ""));
        correttore.correggi(testo, new Correttore.Esito() {
            @Override public void corretto(String originale, String corretto) {
                int i = righe.lastIndexOf(originale);
                if (i < 0) return;      // nel frattempo cancellata
                righe.set(i, corretto);
                mostraSottotitoli();
            }
        });
    }

    /** Comandi vocali: devono essere l'unica cosa detta nella frase. */
    private boolean eseguiComando(String testo) {
        String t = Normalizer.normalize(testo.toLowerCase(Locale.ITALIAN), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^a-z ]", "")
                .trim();
        if (t.equals("cancella") || t.equals("cancella tutto") || t.equals("pulisci")) {
            cancellaTutto();
            return true;
        }
        if (t.equals("piu grande") || t.equals("ingrandisci") || t.equals("scrivi piu grande")) {
            cambiaDimensione(+8);
            return true;
        }
        if (t.equals("piu piccolo") || t.equals("rimpicciolisci") || t.equals("scrivi piu piccolo")) {
            cambiaDimensione(-8);
            return true;
        }
        return false;
    }

    /** Dopo 10 minuti senza voci l'ascolto si spegne da solo (risparmio batteria). */
    private final Runnable controlloSilenzio = new Runnable() {
        @Override public void run() {
            if (ascolto && !inCarica
                    && System.currentTimeMillis() - ultimaVoce > SPEGNI_DOPO_SILENZIO_MS) {
                fermaAscolto();
            }
            handler.postDelayed(this, 30000);
        }
    };

    /** Aggiorna tastone, stato e schermo acceso. */
    private void aggiorna() {
        if (tastone == null) return;
        if (ascolto) {
            tastone.setText("STO ASCOLTANDO\ntocca per fermare");
            tastone.setBackground(sfondo(ROSSO));
            stato.setText(motore == null || motore == motoreVosk ? "Ascolto senza internet"
                    : motore == motoreWhisper ? (prefs.getBoolean("ibrido", true) && modello != null ? "Ascolto con Whisper + Vosk" : "Ascolto con Whisper") : "Ascolto con internet");
        } else if (modelloInCaricamento && !MotoreRete.disponibile(this)
                && indirizzoWhisper().isEmpty()) {
            tastone.setText("PREPARAZIONE…");
            tastone.setBackground(sfondo(GRIGIO));
            stato.setText(" ");
        } else {
            tastone.setText("TOCCA PER ASCOLTARE");
            tastone.setBackground(sfondo(VERDE));
            stato.setText("Ascolto spento");
        }
        mostraSottotitoli();
        schermoAcceso();
    }

    private void schermoAcceso() {
        if (ascolto || inCarica) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    // ================================================================== batteria

    private final BroadcastReceiver ricevitoreBatteria = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            int livello = i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scala = i.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            int statoB = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            int collegato = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            if (livello < 0 || scala <= 0) return;

            int perc = Math.round(livello * 100f / scala);
            inCarica = collegato != 0;
            String t = "Batteria " + perc + "%";
            if (statoB == BatteryManager.BATTERY_STATUS_FULL && inCarica) t += "  –  carica completa";
            else if (inCarica) t += "  –  in carica";
            else if (perc <= 15) t += "  –  METTERE IN CARICA!";
            batteria.setText(t);
            batteria.setBackground(sfondo(perc <= 15 ? ROSSO : (perc <= 35 ? ARANCIO : VERDE)));
            schermoAcceso();
        }
    };

    // ================================================================== modalità chiosco

    private boolean isDeviceOwner() {
        if (Build.VERSION.SDK_INT < 21) return false;
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        return dpm != null && dpm.isDeviceOwnerApp(getPackageName());
    }

    private void preparaChiosco() {
        if (!isDeviceOwner()) return;
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(this, AdminReceiver.class);
        dpm.setLockTaskPackages(admin, new String[]{ getPackageName() });
        if (Build.VERSION.SDK_INT >= 23) {
            try { dpm.setKeyguardDisabled(admin, true); } catch (Exception ignored) { }
        }
    }

    private void avviaChioscoSePossibile() {
        if (!isDeviceOwner()) return;
        try { startLockTask(); } catch (Exception ignored) { }
    }

    private void esciDaChiosco() {
        if (Build.VERSION.SDK_INT >= 21) {
            try { stopLockTask(); } catch (Exception ignored) { }
        }
    }

    // ================================================================== menu tecnico nascosto

    private void toccoSegreto() {
        long ora = System.currentTimeMillis();
        if (ora - primoTocco > 3000) { primoTocco = ora; tocchiSegreti = 0; }
        if (++tocchiSegreti >= 5) { tocchiSegreti = 0; apriMenuTecnico(); }
    }

    /** Il motore che sta ascoltando, o quello che verrebbe usato premendo il tastone. */
    private String nomeMotoreAttuale() {
        MotoreAscolto m = ascolto ? motore : scegliMotoreSenzaCrearlo();
        String nome = m == null ? "nessuno disponibile"
                : m == motoreVosk ? "Vosk (locale, senza internet)"
                : m == motoreRete ? "riconoscimento di sistema (internet)"
                : "server Whisper (" + indirizzoWhisper() + ")";
        return ascolto ? nome + " — in ascolto" : nome + " — spento, userebbe questo";
    }

    private MotoreAscolto scegliMotoreSenzaCrearlo() {
        String modo = modo();
        boolean reteOk = reteDisponibile();
        boolean sistemaOk = MotoreRete.disponibile(this);
        boolean whisperOk = !MotoreWhisper.urlCompleto(indirizzoWhisper()).isEmpty();
        if (modo.equals("whisper")) return whisperOk ? motoreWhisper != null ? motoreWhisper : sentinella() : null;
        if (modo.equals("rete")) return sistemaOk ? motoreRete : null;
        if (modo.equals("auto")) {
            if (reteOk && whisperOk) return motoreWhisper != null ? motoreWhisper : sentinella();
            if (reteOk && sistemaOk) return motoreRete;
        }
        if (motoreVosk != null) return motoreVosk;
        return modo.equals("auto") && sistemaOk ? motoreRete : null;
    }

    /** Segnaposto per "Whisper" quando il motore non è ancora stato creato. */
    private MotoreAscolto sentinella() {
        return new MotoreWhisper(MotoreWhisper.urlCompleto(indirizzoWhisper()), "", "", null, this);
    }

    /** Pannello di configurazione nascosto (5 tocchi sulla barra della batteria). */
    private void apriMenuTecnico() {
        final boolean owner = isDeviceOwner();
        final String[] codici = { "auto", "locale", "whisper", "rete" };
        String[] nomi = { "Automatico (Whisper, internet, poi locale)", "Solo senza internet (Vosk)",
                "Solo server Whisper di casa", "Solo internet di sistema (Google)" };

        LinearLayout colonna = new LinearLayout(this);
        colonna.setOrientation(LinearLayout.VERTICAL);
        colonna.setPadding(dp(20), dp(8), dp(20), dp(8));

        TextView attuale = new TextView(this);
        attuale.setText("Motore in uso ora: " + nomeMotoreAttuale());
        attuale.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        attuale.setTextColor(BLU);
        attuale.setTypeface(Typeface.DEFAULT_BOLD);
        attuale.setPadding(0, 0, 0, dp(12));
        colonna.addView(attuale);

        TextView t1 = new TextView(this);
        t1.setText("Come ascoltare");
        t1.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        t1.setTypeface(Typeface.DEFAULT_BOLD);
        colonna.addView(t1);

        final android.widget.RadioGroup gruppo = new android.widget.RadioGroup(this);
        for (int i = 0; i < nomi.length; i++) {
            android.widget.RadioButton rb = new android.widget.RadioButton(this);
            rb.setId(100 + i);
            rb.setText(nomi[i]);
            rb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            gruppo.addView(rb);
        }
        gruppo.check(100 + java.util.Arrays.asList(codici).indexOf(modo()));
        colonna.addView(gruppo);

        TextView t2 = new TextView(this);
        t2.setText("Whisper: server di casa o online (vuoto = non usato)");
        t2.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        t2.setTypeface(Typeface.DEFAULT_BOLD);
        t2.setPadding(0, dp(16), 0, 0);
        colonna.addView(t2);

        final android.widget.EditText campo = new android.widget.EditText(this);
        campo.setSingleLine(true);
        campo.setHint("192.168.1.20:8000");
        campo.setText(indirizzoWhisper());
        colonna.addView(campo);

        final android.widget.EditText campoChiave = new android.widget.EditText(this);
        campoChiave.setSingleLine(true);
        campoChiave.setHint("Chiave API (solo servizi online)");
        campoChiave.setText(prefs.getString("whisper_chiave", ""));
        colonna.addView(campoChiave);

        final android.widget.EditText campoModello = new android.widget.EditText(this);
        campoModello.setSingleLine(true);
        campoModello.setHint("Modello (vuoto = whisper-1)");
        campoModello.setText(prefs.getString("whisper_modello", ""));
        colonna.addView(campoModello);

        final android.widget.CheckBox ibrido = new android.widget.CheckBox(this);
        ibrido.setText("Mostra subito il testo di Vosk, poi corregge Whisper");
        ibrido.setChecked(prefs.getBoolean("ibrido", true));
        colonna.addView(ibrido);

        Button groq = new Button(this);
        groq.setText("Compila per Groq (online)");
        groq.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                campo.setText("https://api.groq.com/openai/v1/audio/transcriptions");
                campoModello.setText("whisper-large-v3");
            }
        });
        colonna.addView(groq);

        Button google = new Button(this);
        google.setText("Compila per Google Cloud (online)");
        google.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                campo.setText("https://speech.googleapis.com/v1/speech:recognize");
                campoModello.setText("");
            }
        });
        colonna.addView(google);

        TextView t3 = new TextView(this);
        t3.setText("Correzione con AI (Ollama sul PC, vuoto = spenta)");
        t3.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        t3.setTypeface(Typeface.DEFAULT_BOLD);
        t3.setPadding(0, dp(16), 0, 0);
        colonna.addView(t3);

        final android.widget.EditText campoLlm = new android.widget.EditText(this);
        campoLlm.setSingleLine(true);
        campoLlm.setHint("192.168.1.20:11434");
        campoLlm.setText(prefs.getString("llm", ""));
        colonna.addView(campoLlm);

        final android.widget.EditText campoLlmModello = new android.widget.EditText(this);
        campoLlmModello.setSingleLine(true);
        campoLlmModello.setHint("Modello (vuoto = qwen2.5:3b)");
        campoLlmModello.setText(prefs.getString("llm_modello", ""));
        colonna.addView(campoLlmModello);

        final TextView esito = new TextView(this);
        esito.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        Button prova = new Button(this);
        prova.setText("Prova connessione");
        prova.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                final String url = MotoreWhisper.urlCompleto(campo.getText().toString());
                if (url.isEmpty()) { esito.setText("Scrivi prima l'indirizzo."); return; }
                esito.setText("Provo…");
                new Thread(new Runnable() {
                    @Override public void run() {
                        final boolean ok = MotoreWhisper.raggiungibile(url);
                        handler.post(new Runnable() {
                            @Override public void run() {
                                esito.setText(ok ? "✔ Server raggiungibile" : "✘ Server non raggiungibile");
                                esito.setTextColor(ok ? VERDE : ROSSO);
                            }
                        });
                    }
                }).start();
            }
        });
        colonna.addView(prova);
        colonna.addView(esito);

        Button android = new Button(this);
        android.setText("Impostazioni Android");
        android.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { esciDaChiosco(); apri(new Intent(Settings.ACTION_SETTINGS)); }
        });
        colonna.addView(android);

        Button home = new Button(this);
        home.setText(owner ? "Disattiva modalità chiosco (definitivo)" : "Scegli un'altra schermata Home");
        home.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (owner) disattivaChiosco(); else scegliHome(); }
        });
        colonna.addView(home);

        ScrollView scroll = new ScrollView(this);
        scroll.addView(colonna);

        new AlertDialog.Builder(this)
                .setTitle("Configurazione")
                .setView(scroll)
                .setPositiveButton("Salva", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface d, int which) {
                        int sel = gruppo.getCheckedRadioButtonId() - 100;
                        prefs.edit()
                                .putString("modo", codici[Math.max(0, sel)])
                                .putString("whisper", campo.getText().toString().trim())
                                .putString("whisper_chiave", campoChiave.getText().toString().trim())
                                .putString("whisper_modello", campoModello.getText().toString().trim())
                                .putBoolean("ibrido", ibrido.isChecked())
                                .putString("llm", campoLlm.getText().toString().trim())
                                .putString("llm_modello", campoLlmModello.getText().toString().trim())
                                .apply();
                        if (correttore != null) { correttore.chiudi(); correttore = null; }
                        if (ascolto) { fermaAscolto(); avviaAscolto(); }
                        aggiorna();
                    }
                })
                .setNegativeButton("Chiudi", null)
                .show();
    }

    @SuppressWarnings("deprecation")
    private void disattivaChiosco() {
        esciDaChiosco();
        DevicePolicyManager dpm = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
        try {
            if (Build.VERSION.SDK_INT >= 23) {
                dpm.setKeyguardDisabled(new ComponentName(this, AdminReceiver.class), false);
            }
            dpm.clearDeviceOwnerApp(getPackageName());
            avviso("Modalità chiosco disattivata.");
        } catch (Exception e) {
            avviso("Impossibile disattivare: " + e.getMessage());
        }
    }

    private void scegliHome() {
        if (Build.VERSION.SDK_INT >= 21) {
            apri(new Intent(Settings.ACTION_HOME_SETTINGS));
        } else {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            i.setData(Uri.parse("package:" + getPackageName()));
            apri(i);
        }
    }

    private void apri(Intent i) {
        try {
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            avviso("Schermata non disponibile su questo dispositivo.");
        }
    }

    private void avviso(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
    }
}
