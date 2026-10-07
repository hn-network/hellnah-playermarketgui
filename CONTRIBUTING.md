# Berkontribusi ke HellNah-PlayerMarketGui

Kontribusi berupa perbaikan bug, fitur, dokumentasi, dan pengujian dapat diajukan melalui Pull Request. Baca [README.md](README.md) dan [PANDUAN.md](PANDUAN.md) sebelum mulai.

## Melaporkan bug atau mengusulkan fitur

Periksa Issues yang sudah ada agar laporan tidak duplikat. Untuk laporan bug, sertakan:

- Versi plugin, Paper, Java, MySQL, dan plugin economy yang digunakan.
- Langkah untuk mengulangi masalah.
- Hasil yang diharapkan dan hasil yang terjadi.
- Log error atau screenshot yang relevan, setelah informasi rahasia dihapus.
- Untuk masalah transaksi: apakah item atau uang sudah berpindah dan apakah ada transaksi pending.

Untuk usulan fitur, jelaskan kebutuhan pemain/admin dan contoh alurnya. Diskusikan perubahan besar terlebih dahulu melalui Issue.

Jangan mengirim password, token, config produksi lengkap, atau dump database pemain. Untuk celah duplikasi, kehilangan item/uang, atau akses tidak sah, hubungi maintainer secara privat terlebih dahulu; jangan mempublikasikan langkah eksploitasi aktif di Issue umum.

## Menyiapkan lingkungan pengembangan

Gunakan:

- JDK 21.
- Maven 3.9 atau lebih baru.
- Server Paper 1.21.11 untuk pengujian GUI.
- Database MySQL khusus pengujian; workflow GitHub memakai MySQL 8.4.
- Vault dan economy provider atau PlayerPoints, sesuai integrasi yang diuji.

Fork repository, lalu clone fork milikmu. Ganti URL contoh dengan URL fork sebenarnya:

```bash
git clone https://github.com/USERNAME/HellNah-PlayerMarketGui.git
cd HellNah-PlayerMarketGui
git switch -c fix/nama-perbaikan
```

Jika mengerjakan fitur, gunakan nama branch seperti `feature/nama-fitur`.

## Build dan pengujian

Jalankan dari folder yang berisi `pom.xml`:

```bash
mvn clean package
```

Hasil build berada di `target/`. Command ini menjalankan unit tests, tetapi tidak menjalankan integration tests MySQL tanpa profil `mysql-it`.

Untuk menjalankan unit tests saja:

```bash
mvn test
```

Untuk integration tests, buat database uji terpisah, lalu isi environment berikut. Contoh Bash/Git Bash:

```bash
export PMARKET_TEST_HOST=127.0.0.1
export PMARKET_TEST_PORT=3306
export PMARKET_TEST_DATABASE=hellnah_market_test
export PMARKET_TEST_USER=market_test
export PMARKET_TEST_PASSWORD='PASSWORD_DATABASE_UJI'
mvn -Pmysql-it clean verify
```

Di PowerShell, gunakan bentuk `$env:PMARKET_TEST_HOST = "127.0.0.1"` untuk setiap variable, lalu jalankan command Maven yang sama.

Profil integration test membutuhkan database yang sudah dibuat dan user dengan izin membuat serta menghapus tabel uji. Setiap test menggunakan prefix acak dan membersihkan tabelnya sendiri. Jangan menggunakan database produksi. Lihat PANDUAN.md untuk detail.

Untuk perubahan GUI, inventory, permission, atau economy, lakukan pengujian tambahan di server percobaan dengan minimal dua akun. Cek jual, beli, claim, pembatalan, inventory penuh, filter, navigasi, dan restart sesuai bagian yang berubah. Sertakan hasilnya pada Pull Request. Jangan menyatakan tes lulus jika belum dijalankan.

## Aturan perubahan kode

- Fokuskan satu Pull Request pada satu masalah atau fitur yang berkaitan.
- Ikuti gaya kode di file yang diedit dan gunakan nama yang jelas.
- Pertahankan kompatibilitas Java 21 dan target Paper yang tercantum di project.
- Jangan membundel SQLite, MySQL driver, atau Paper API ke JAR plugin. MySQL driver dimuat terpisah melalui `plugin.yml`.
- Jika versi driver diubah, selaraskan `pom.xml` dan `plugin.yml`.
- Jangan memasukkan credential asli, JAR, folder `target/`, file server runtime, atau backup database ke Git.
- Gunakan parameter SQL untuk nilai dari pemain/config; identifier tabel harus tetap divalidasi.
- Pertahankan validasi kepemilikan, state transaksi, kapasitas, dan konfirmasi yang terkait dengan perubahan.
- Jangan mengulang otomatis operasi pembayaran/item yang hasilnya belum pasti. Perhatikan transaksi pending dan recovery.
- Untuk perubahan schema atau backend, jelaskan dampak terhadap data lama dan langkah migrasi.
- Perbarui config contoh dan dokumentasi jika perilaku atau pengaturan berubah.
- Tambahkan pengujian yang relevan untuk perubahan transaksi atau validasi penting.

## Mengirim Pull Request

Periksa perubahan sebelum commit:

```bash
git status
git diff
```

Kemudian commit dan push branch yang dikerjakan:

```bash
git add src README.md PANDUAN.md CONTRIBUTING.md pom.xml
git commit -m "Jelaskan perbaikan secara singkat"
git push -u origin fix/nama-perbaikan
```

Sesuaikan daftar file dengan perubahanmu; hanya stage file yang memang ingin diajukan. Sesuaikan nama branch jika berbeda.

Buat Pull Request dari branch di fork ke branch `main` repository utama. Sertakan:

- Masalah yang diperbaiki atau kebutuhan fitur.
- Ringkasan perubahan dan contoh perilaku sebelum/sesudah jika membantu.
- Nomor Issue terkait, jika ada.
- Command pengujian dan hasilnya; jelaskan pengujian yang belum dilakukan.
- Screenshot jika tampilan GUI berubah.
- Dampak terhadap config, schema, data lama, atau plugin lain.

Workflow GitHub menjalankan build serta tes MySQL. Tunggu hasil pemeriksaan dan review maintainer. Penggabungan Pull Request dilakukan oleh maintainer setelah perubahan diperiksa.
