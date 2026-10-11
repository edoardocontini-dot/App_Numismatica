# Numismatica

App Android (in italiano) per cercare monete, banconote ed esonumia nel catalogo [Numista](https://it.numista.com)
e tenere traccia della propria collezione.

- **Cerca**: testo, paese, tipo, anno; spunta "Ce l'ho" per aggiungere un pezzo con anno (menu con
  millesimi, segni di zecca e tirature da Numista), conservazione, quantità, provenienza e note.
- **Scheda**: foto dritto/rovescio, materiale, peso, misure, stato (in corso / fuori corso), zecca, legende.
- **Collezione**: filtri per paese, tipo, materiale, stato, periodo.
- **Excel**: catalogo completo (foglio "Collezione") + foglio "Statistiche".
- **Impostazioni**: chiave API Numista, contatore richieste mensili, backup/ripristino in JSON.

La collezione è salvata solo sul telefono. I dati del catalogo sono forniti da Numista
(piano gratuito: 2.000 richieste al mese; ricerche e schede vengono memorizzate sul telefono).

## Build

Ogni push su `main` compila l'APK con GitHub Actions e lo pubblica nelle Releases:
`https://github.com/<owner>/<repo>/releases/latest/download/numismatica.apk`

L'APK è firmato sempre con la stessa chiave (`app/numismatica.keystore`), così le nuove versioni
si installano sopra la precedente senza perdere la collezione.
