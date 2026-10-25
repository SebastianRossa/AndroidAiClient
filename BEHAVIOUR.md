# Bedrock Chat – Gewünschtes Verhalten (Referenz für Portierungen)

Dieses Dokument beschreibt das Soll-Verhalten der App aus Nutzersicht.
Es dient als Grundlage für Portierungen (iOS, Web) und als Abnahmekriterium
für neue Features oder Refactorings.

---

## 1. Gesprächsverlauf (History-Screen)

### 1.1 Öffnen des Verlaufs

- Nach dem Öffnen des Verlaufs-Screens sind **alle** gespeicherten Konversationen
  sichtbar – keine Einträge werden durch die Toolbar verdeckt oder befinden sich
  unsichtbar oberhalb des sichtbaren Bereichs.
- Die **neueste Konversation** (höchste `updatedAt`-Zeit) erscheint ganz oben,
  direkt unterhalb der Toolbar – ohne jeglichen Leerraum dazwischen.
- Die Liste ist nach `updatedAt DESC` sortiert (neueste zuerst).

### 1.2 Erscheinen neuer Einträge

- Wird im Hintergrund eine Konversation gespeichert (z. B. läuft gerade ein
  API-Aufruf), während der Verlauf bereits geöffnet ist, erscheint der neue
  Eintrag **sofort und automatisch** oben in der Liste.
- Der RecyclerView/ScrollView scrollt automatisch an Position 0, sobald ein
  neuer Eintrag als erstes Element eingefügt wird.
- Bestehende Einträge bleiben an ihrer Position; nur vorne eingefügte Einträge
  lösen ein Scroll-to-Top aus.

### 1.3 Scroll-Verhalten bei Updates

| Situation | Scroll? |
|---|---|
| Erster Load (Liste war leer → Einträge erscheinen) | **Ja**, zu Position 0 |
| Neuer Eintrag wird vorne eingefügt | **Ja**, zu Position 0 |
| Eintrag in der Mitte oder am Ende wird gelöscht | **Nein** |
| Inhaltsänderung am ersten Eintrag (z. B. Nachrichtenanzahl) | **Nein** |

### 1.4 Zurücknavigieren aus dem Verlauf

- Wählt der Nutzer eine Konversation aus der Liste und navigiert zurück zum
  Chat, wird diese Konversation korrekt geladen.
- Kehrt der Nutzer erneut in den Verlauf zurück, ist das Verhalten identisch
  mit Punkt 1.1 (alle Einträge sichtbar, neueste oben).

### 1.5 Löschen

- Tippt der Nutzer auf den Löschen-Button eines Eintrags, verschwindet dieser
  sofort aus der Liste.
- Die verbleibenden Einträge rücken nach oben; kein unbeabsichtigtes Scrollen.

---

## 2. Chat-Screen

### 2.1 Layout und Insets

- Der Chat-Bereich (Nachrichtenliste) beginnt direkt unterhalb der Toolbar.
- Die Eingabeleiste (`InputBar`) ist stets sichtbar – weder von der
  Navigationsleiste noch von der virtuellen Tastatur verdeckt.
- Öffnet sich die Tastatur, schiebt sich die `InputBar` **animiert** nach oben,
  sodass sie direkt über der Tastatur liegt. Die Nachrichtenliste verkleinert
  sich entsprechend.
- Schließt sich die Tastatur, kehrt die `InputBar` animiert in ihre
  Ausgangsposition zurück.

### 2.2 Konversation laden

- Wählt der Nutzer eine Konversation im Verlauf aus, werden deren Nachrichten
  vollständig und in der richtigen Reihenfolge (älteste oben, neueste unten)
  angezeigt.
- Die Ansicht scrollt automatisch zur neuesten Nachricht.

### 2.3 Neue Konversation starten

- Über den Menüpunkt „Neues Gespräch" wird der Chat-Bereich geleert.
  Die bisherige Konversation bleibt in der Datenbank erhalten und erscheint
  weiterhin im Verlauf.

### 2.4 Persistenz

- Jede Konversation wird nach dem Eintreffen der Antwort vollständig
  (Nutzer- **und** Assistenten-Nachricht) gespeichert.
- Beim nächsten App-Start ist sie im Verlauf auffindbar und kann vollständig
  wiederhergestellt werden.

### 2.5 Antwortlänge

- Assistenten-Antworten werden **vollständig** dargestellt, auch bei langen
  Texten (mehrere tausend Zeichen / Tokens).
- Der `maxTokens`-Parameter in `Defaults.kt` bestimmt die maximale Antwortlänge
  serverseitig. Er muss mindestens **4096** betragen, damit typische Antworten
  nicht abgeschnitten werden.
- Die App selbst hat **keine clientseitige Längenbegrenzung** für den
  Nachrichtentext.

---

## 3. Edge-to-Edge / Insets (technische Anforderung)

Diese Anforderung gilt für alle Screens:

- Die App nutzt den gesamten Bildschirm (edge-to-edge), d. h. Inhalte erstrecken
  sich hinter StatusBar und Navigationsleiste.
- Die **Toolbar** deckt die StatusBar visuell ab (Hintergrundfarbe der Toolbar
  reicht bis zur Bildschirmoberkante).
- **Kein Inhalt** wird von der StatusBar, der Toolbar oder der Navigationsleiste
  verdeckt.
- **Implementierungsregel:** Die Toolbar gehört zum Activity-Layout (nicht als
  System-ActionBar). Ein `AppBarLayout` mit `fitsSystemWindows="true"` handhabt
  den StatusBar-Abstand automatisch. Fragments behandeln ausschließlich
  Bottom-Insets (Navigationsleiste + IME).

---

## 4. Fehlerbehandlung

- Schlägt ein API-Aufruf fehl (z. B. ungültige Credentials, Netzwerkfehler),
  erscheint ein Fehlerbanner im Chat-Screen.
- Das Banner kann durch den Nutzer oder durch einen erneuten erfolgreichen
  Aufruf geschlossen werden.
- Bei ungültigen AWS-Credentials wird der Nutzer automatisch zum
  Einstellungs-Screen weitergeleitet.

---

## 5. Technologische Constraints (Android-Referenzimplementierung)

| Aspekt | Entscheidung |
|---|---|
| Datenbankschicht | Room (SQLite), reaktiv via `Flow` |
| Sortierung Verlauf | `ORDER BY updatedAt DESC` |
| Scroll-Trigger | Nur wenn sich die ID des ersten Listeneintrags ändert |
| IME-Handling | `adjustNothing` + manuelles Bottom-Padding via `WindowInsetsAnimationCompat` |
| Insets Top | Vom `AppBarLayout` (fitsSystemWindows) übernommen – kein manuelles Padding in Fragments |
| Insets Bottom | `systemBars() or ime()` per `setOnApplyWindowInsetsListener` + `requestApplyInsets` nach Navigation |
