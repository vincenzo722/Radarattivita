package it.violante.radarattivita;

import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivityCRMFixed extends MainActivityCRM {
    private int searchGeneration = 0;
    private boolean zoneMode = false;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        renameButton(findViewById(android.R.id.content), "VICINE", "AGGIORNA RADAR");

        final int[] lastCat = { crmCategory.getSelectedItemPosition() };
        crmCategory.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position != lastCat[0]) {
                    lastCat[0] = position;
                    selectionChanged();
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });

        final int[] lastRadius = { radius.getSelectedItemPosition() };
        radius.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position != lastRadius[0]) {
                    lastRadius[0] = position;
                    selectionChanged();
                }
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
    }

    private void selectionChanged() {
        searchGeneration++;
        clearRadar();
        status.setText("Selezione cambiata • premi AGGIORNA RADAR");
    }

    private void clearRadar() {
        if (radar != null) {
            radar.places = new ArrayList<>();
            radar.max = meters();
            radar.invalidate();
        }
    }

    private void renameButton(View v, String oldText, String newText) {
        if (v instanceof Button) {
            Button b = (Button)v;
            if (oldText.equalsIgnoreCase(b.getText().toString().trim())) b.setText(newText);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup)v;
            for (int i=0; i<g.getChildCount(); i++) renameButton(g.getChildAt(i), oldText, newText);
        }
    }

    @Override void scanCategory() {
        int p = crmCategory.getSelectedItemPosition();
        String z = zoneEdit == null ? "" : zoneEdit.getText().toString().trim();

        if (zoneMode && z.length() >= 2 && radarCenter != null) {
            radar.here = radarCenter;
        } else {
            zoneMode = false;
            radarCenter = here;
            if (radarCenter == null) {
                clearRadar();
                status.setText("Attendo posizione GPS…");
                return;
            }
            radar.here = radarCenter;
        }

        if (p == 9) {
            clearRadar();
            showCustomCompanies();
            return;
        }
        runSearch(categoryFilter(Math.min(p,8)), crmCats[p]);
    }

    @Override void searchZone() {
        String z = zoneEdit.getText().toString().trim();
        if (z.length() < 2) {
            zoneMode = false;
            status.setText("Scrivi il Comune o la zona.");
            return;
        }

        searchGeneration++;
        clearRadar();
        status.setText("Cerco la zona " + z + "…");
        new Thread(() -> {
            try {
                Geocoder g = new Geocoder(this, Locale.ITALY);
                List<Address> l = g.getFromLocationName(z, 1);
                if (l == null || l.isEmpty()) throw new Exception("zona non trovata");
                Address ad = l.get(0);
                Location c = new Location("zona");
                c.setLatitude(ad.getLatitude());
                c.setLongitude(ad.getLongitude());
                runOnUiThread(() -> {
                    zoneMode = true;
                    radarCenter = c;
                    radar.here = c;
                    int p = crmCategory.getSelectedItemPosition();
                    if (p == 9) showCustomCompanies();
                    else runSearch(categoryFilter(Math.min(p,8)), crmCats[p]);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    zoneMode = false;
                    clearRadar();
                    status.setText("Zona non trovata. Riprova con Comune e provincia.");
                });
            }
        }).start();
    }

    @Override void runSearch(String filter, String label) {
        final int myGeneration = ++searchGeneration;
        final int requestedMeters = meters();
        clearRadar();
        status.setText("Aggiorno radar • " + label + "…");

        new Thread(() -> {
            String lastError = "";
            String query = makeQuery(filter);
            for (int i=0; i<overpassServers.length; i++) {
                try {
                    final int serverNo = i + 1;
                    runOnUiThread(() -> {
                        if (myGeneration == searchGeneration)
                            status.setText("Ricerca " + label + "… server " + serverNo);
                    });
                    String json = callOverpass(overpassServers[i], query);
                    ArrayList<Place> ps = parse(json);
                    runOnUiThread(() -> {
                        if (myGeneration != searchGeneration) return;
                        radar.places = ps;
                        radar.max = requestedMeters;
                        radar.invalidate();
                        if (ps.isEmpty()) status.setText("Nessun risultato nel raggio selezionato.");
                        else status.setText(ps.size() + " risultati • radar aggiornato");
                    });
                    return;
                } catch (Exception e) {
                    lastError = e.getClass().getSimpleName() + ": " +
                            (e.getMessage() == null ? "errore" : e.getMessage());
                }
            }
            final String err = lastError;
            runOnUiThread(() -> {
                if (myGeneration != searchGeneration) return;
                clearRadar();
                status.setText("Ricerca non riuscita. " + err);
            });
        }).start();
    }
}
