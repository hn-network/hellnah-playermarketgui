# HellNah-PlayerMarketGui

Player market GUI untuk Paper 1.21.11 / Java 21 dengan penyimpanan MySQL yang dapat diatur melalui config. Versi source: 1.1.0.

## Build

Butuh JDK 21 dan Maven 3.9+:

```bash
mvn clean package
```

Output: `target/HellNah-PlayerMarketGui-1.1.0.jar`.

**Paket source ini belum dibuild dan tes MySQL belum dijalankan oleh pembuatnya.** Tidak berisi JAR atau dependency binary. SQLite dihapus. Driver MySQL dipasang terpisah oleh Paper melalui field libraries pada plugin.yml, sehingga tidak dibundel ke JAR hasil build.

## Setup database

Buat database/user lewat panel VPS, lalu isi bagian `storage.mysql` pada config runtime: host, port, database, username, password, table-prefix dan SSL. Target: MySQL 8.0.16+ dengan InnoDB. Database dibuat lewat panel; tabel dibuat otomatis oleh plugin. Lihat [PANDUAN.md](PANDUAN.md) untuk TLS, environment password, permissions dan recovery.

Default payment: Vault + economy provider. PlayerPoints dan API currency custom juga tersedia.

## Commands

- `/pmarket`: buka GUI utama.
- `/pmarket NamaPlayer`: buka shop pemain.
- `/pmarket sell <harga total> [currency]`: jual seluruh stack di tangan.
- `/pmarket search <kata>`: cari item.
- `/pmarket claim`: ambil item yang dibeli, dibatalkan atau expired.
- `/pmarket reload`: admin; perubahan storage memerlukan restart.

## GUI

Market Players, Browse Item yang dikelompokkan per material, kategori jenis item, pencarian item/shop, Your Market, saldo hasil penjualan dan Unclaimed Items. Listing mendukung filter, konfirmasi, pembatalan, masa kedaluwarsa dan pagination.

Kapasitas jual: `hellnah.pmarket.pages.1` sampai `.10`, mapping config, bawaan 45 listing per halaman. Hasil penjualan disimpan dalam escrow market dan ditarik lewat Your Market > Saldo Hasil Penjualan.

## GitHub

Upload isi project ke root repository, termasuk `.github/workflows/build.yml`. Workflow membuild dan menguji memakai MySQL 8.4 sementara, lalu menyimpan JAR sebagai Actions Artifact. Jangan commit password VPS, config runtime atau database backup. `.gitignore` menyaring binary dan file runtime; contoh config di src/main/resources tetap ikut repository.

## Tests

`mvn test` menjalankan unit tests settings. Untuk MySQL integration tests, isi environment PMARKET_TEST_* pada database uji lalu gunakan `mvn -Pmysql-it clean verify`; lihat panduan.

## Upgrade dari 1.0.0

Database SQLite lama tidak dimigrasikan otomatis. Backup dan selesaikan escrow lama sebelum pindah ke MySQL baru. Source versi ini mempertahankan state transaksi tertunda dan command recovery, tetapi file inventory, database market dan economy eksternal tidak atomik bersama.
