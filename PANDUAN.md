# HellNah-PlayerMarketGui v1.1.0

Plugin market untuk **Paper 1.21.11, Java 21**. Pasang di server Paper realm, bukan di Velocity. Satu database untuk satu server; versi ini tidak melakukan sinkronisasi market antar-realm.

## Source project dan pemasangan

Paket ini hanya berisi source Maven, config contoh, dokumentasi dan workflow GitHub. Tidak ada JAR, folder target, driver database, hasil compile atau laporan tes lama. Build belum dijalankan untuk versi MySQL ini sesuai permintaan.

1. Buat database dan user melalui panel VPS. Gunakan host, port, nama database lengkap (termasuk prefix panel), username dan password yang ditampilkan panel.
2. Isi `src/main/resources/config.yml` menggunakan placeholder contoh, atau lebih aman edit config runtime setelah JAR dipasang. Jangan commit password produksi ke repository GitHub.
3. Build sendiri dengan JDK 21 dan Maven 3.9+: `mvn clean package` dari folder yang berisi pom.xml.
4. Hasil build berada di `target/HellNah-PlayerMarketGui-1.1.0.jar`. Pasang di plugins server Paper, bukan Velocity.
5. Sediakan Vault + economy provider untuk money, atau konfigurasi PlayerPoints.
6. Saat pertama start, Paper mengambil driver MySQL terpisah melalui `plugin.yml` libraries. Server perlu akses ke repository Maven yang digunakan Paper. Driver tidak dibundel ke JAR plugin.
7. Config runtime dibuat di `plugins/HellNah-PlayerMarketGui/config.yml`. Jika koneksi database belum benar, plugin menolak aktif; edit config dan restart.

Database tidak dibuat otomatis oleh plugin. Tabel berprefix dibuat otomatis sesudah koneksi berhasil. User database memerlukan SELECT, INSERT, UPDATE, CREATE dan INDEX pada database market. DELETE/DROP tidak diperlukan saat runtime. Pengujian integrasi membutuhkan DROP untuk membersihkan tabel uji.

Versi ini memakai MySQL (InnoDB), dengan target MySQL 8.0.16+; workflow contoh menguji MySQL 8.4. Driver yang dipin adalah Connector/J 9.7.0. Server MariaDB dari panel tidak termasuk target yang diuji oleh workflow ini; jangan menganggap hasil CI MySQL memverifikasi MariaDB.

Jika panel membatasi akses, gunakan IP/hostname database dari panel dan izinkan IP server Minecraft sebagai client. `127.0.0.1` hanya benar jika database dapat diakses dari host/container server Minecraft yang sama; alamat lokal host VPS belum tentu alamat lokal container.

**Data SQLite versi 1.0.0 tidak diimpor otomatis.** Simpan backup market.db dan data player/economy lama. MySQL baru akan mulai kosong; jangan mengganti backend di server yang mempunyai escrow lama tanpa menuntaskan atau merencanakan migrasinya dahulu. Paket source ini tidak memuat alat migrasi SQLite.

## Konfigurasi MySQL

```yaml
storage:
  type: MYSQL
  mysql:
    host: 'HOST_DARI_PANEL'
    port: 3306
    database: 'NAMA_DATABASE_LENGKAP_DARI_PANEL'
    username: 'USERNAME_DARI_PANEL'
    password: 'PASSWORD_DARI_PANEL'
    password-env: ''
    table-prefix: 'hnpm_'
    ssl-mode: REQUIRED
    allow-public-key-retrieval: false
    connect-timeout-ms: 3000
    socket-timeout-ms: 3000
```

MYSQL adalah satu-satunya backend pada versi ini; SQLite dihapus. SSL mode tersedia: DISABLED, PREFERRED, REQUIRED, VERIFY_CA, VERIFY_IDENTITY. REQUIRED membutuhkan TLS tetapi belum memverifikasi identitas host. Untuk database remote dengan sertifikat yang dipercaya JVM, gunakan VERIFY_IDENTITY. Untuk koneksi lokal yang memang tidak menyediakan TLS, gunakan DISABLED. Pengaturan PREFERRED dapat jatuh ke koneksi tanpa TLS.

`allow-public-key-retrieval` bawaan false. Jika MySQL caching_sha2_password memerlukan pengambilan public key dan koneksi lokal tidak memakai TLS, properti ini dapat diaktifkan sesuai konfigurasi database; prioritaskan TLS untuk database remote.

Password bisa disuplai lewat environment server:

```yaml
password: ''
password-env: 'HN_PMARKET_DB_PASSWORD'
```

Jika password-env diisi, variable tersebut harus tersedia saat start. Nilainya menggantikan password inline. JDBC URL tidak memasukkan credentials, dan object settings meredaksi credentials dalam toString. Tetap jangan membagikan config runtime atau log database berisi informasi rahasia.

`/pmarket reload` memuat GUI, listing, permission mapping dan pesan. Perubahan host, port, database, username, password, prefix atau pengaturan koneksi memerlukan **restart**, supaya operasi tertunda tidak berpindah database di tengah proses.

Satu prefix untuk satu server Paper. Nama prefix hanya huruf, angka, underscore, dimulai huruf, panjang maksimal 31 karakter. Gunakan prefix berbeda untuk realm yang berbeda, misalnya `hn_survival_` dan `hn_anarchy_`. Versi ini tetap tidak mendukung market lintas server; koneksi khusus memegang advisory lock MySQL sehingga server kedua yang memakai database/prefix sama ditolak.

## GUI dan alur

Menu utama menyediakan Market Players, Browse Item, Kategori Item, Search Item, Search Shop, Your Market dan Unclaimed Items.

**Market Players:** kepala pemain yang memiliki listing aktif. Klik kepala untuk melihat shop pemain tersebut. Shop kosong tidak ditampilkan di daftar umum.

**Browse Item:** satu ikon per Material Minecraft, bukan setiap penjual. Klik GLASS misalnya, lalu muncul listing GLASS dari semua pemain. Item custom dengan base material sama ikut kelompok itu, tetapi metadata masing-masing tetap dipertahankan.

**Kategori Item:** jenis item memakai daftar Material dan wildcard di `categories`. Contoh `*_LEAVES` masuk Dekorasi. Urutan kategori pertama yang cocok dipakai; item yang belum dipetakan masuk Lainnya. Kategori mengacu base material; nama item custom bisa dicari lewat Search Item.

**Search Item:** cari Material, nama custom, atau lore. Contoh `diamond`, `diamond sword`, atau potongan nama senjata. Pencarian tidak membedakan huruf besar/kecil. Nama material standar menggunakan bahasa Inggris; nama/lore custom mengikuti teks item.

**Search Shop:** cari sebagian nama pemain, lalu pilih kepala pemain.

**Your Market:** listing aktif milikmu, tombol jual item di tangan, serta tombol Saldo Hasil Penjualan. Klik listing sendiri untuk membatalkannya setelah konfirmasi. Item dikembalikan ke Unclaimed Items; tidak dihancurkan.

**Unclaimed Items:** pembelian, listing dibatalkan, dan listing kedaluwarsa. Klik satu item untuk mengambilnya. Butuh satu slot storage kosong; item tidak dilempar ke tanah saat inventory penuh. Item di sini tidak kedaluwarsa lagi.

Halaman menyediakan Back, Previous/Next dan Info. Listing dapat diurutkan: Terbaru, Termurah, Termahal, Segera Kedaluwarsa, Nama Item. Tombol mata uang memfilter satu currency. Jika Semua dipilih, harga dikelompokkan per mata uang; money dan points tidak dianggap punya kurs yang sama.

Harga merupakan **harga total seluruh item dalam satu listing**, bukan harga per item. Pembeli mengambil seluruh stack; pembelian sebagian tidak tersedia.

## Jual, beli, dan pembayaran

Pegang item di tangan utama, lalu:

```text
/pmarket sell 10000
/pmarket sell 10000 money
/pmarket sell 150 points
```

Atau buka Your Market, klik Jual Item di Tangan, lalu ketik `10000 money` di chat. Ada konfirmasi sebelum item dipindahkan. Input chat berlaku 60 detik; `cancel` membatalkan. Seluruh stack yang dipegang dijual. Jika hanya ingin menjual 1 item dari stack, pisahkan stack terlebih dahulu.

Setelah konfirmasi beli, uang dipotong, item masuk Unclaimed Items pembeli dan saldo hasil penjualan masuk **saldo internal market** penjual. Penjual membuka Your Market > Saldo Hasil Penjualan > pilih mata uang > konfirmasi penarikan. Payout dilakukan ke provider asli, termasuk money Vault atau PlayerPoints. Penjual yang offline bisa menarik setelah login.

Penjual online mendapat pesan nama pembeli, nama item dan hasil bersih. Pembeli mendapat pesan sukses dan petunjuk pengambilan. Saat login, pemain diingatkan jika ada unclaimed item atau saldo hasil penjualan.

Saldo market bukan saldo Vault/PlayerPoints langsung dan tidak bisa dipakai membeli sebelum ditarik. Ini membuat penerimaan uang penjual offline tidak bergantung pada deposit otomatis yang bisa gagal.

## Command

| Command | Fungsi |
|---|---|
| `/pmarket` | Menu utama |
| `/pmarket NamaPlayer` | Shop pemain tersebut |
| `/pmarket sell <harga> [currency]` | Jual seluruh stack di tangan |
| `/pmarket claim` | Unclaimed Items |
| `/pmarket search <kata>` | Search Item tanpa input chat |
| `/pmarket help` | Bantuan singkat |
| `/pmarket reload` | Muat ulang config; admin |
| `/pmarket pending` | Daftar transaksi dikarantina; admin |
| `/pmarket resolve <id\|payout:id> <commit\|abort>` | Penyelesaian manual setelah verifikasi; admin |

## Permission kapasitas

| Permission | Default | Fungsi |
|---|---|---|
| `hellnah.pmarket.use` | Semua pemain | Membuka market dan membeli/claim |
| `hellnah.pmarket.sell` | Semua pemain | Menjual item |
| `hellnah.pmarket.admin` | OP | Reload dan recovery |
| `hellnah.pmarket.pages.1` | false | Level 1; bawaan 1 halaman |
| `hellnah.pmarket.pages.2` | false | Level 2; bawaan 2 halaman |
| `hellnah.pmarket.pages.3` ... `.10` | false | Level 3–10; bawaan 3–10 halaman |

Tanpa permission level, player punya `capacity.default-pages: 1`. Satu halaman bawaan berisi **45 listing**; 10 halaman = **450 listing aktif**. Permission mengatur kapasitas jual milik pemain, bukan membatasi halaman saat browsing shop orang lain.

Jika pemain mewarisi beberapa permission level, nilai halaman terbesar berlaku. OP tidak otomatis mendapat 10 halaman; node halaman default false. Item yang sudah dibeli, expired atau dibatalkan tidak memakan kapasitas jual; listing dalam tahap jual/beli tertunda tetap dihitung.

Contoh LuckPerms:

```text
/lp group default permission set hellnah.pmarket.pages.1 true
/lp group <nama_rank_kedua> permission set hellnah.pmarket.pages.2 true
/lp group <nama_rank_ketiga> permission set hellnah.pmarket.pages.3 true
/lp group <nama_rank_tertinggi> permission set hellnah.pmarket.pages.10 true
/lp group <nama_rank_admin> permission set hellnah.pmarket.admin true
```

Ganti placeholder nama rank dengan group sebenarnya; tanda `<...>` tidak ikut diketik. Tidak perlu menetapkan semua level sekaligus. Mapping `capacity.levels` bisa diubah, misalnya level permission 2 diberi 3 halaman. Batas mapping 1–10 halaman. Jika rank diturunkan dan listing lebih banyak daripada kapasitas baru, listing lama tetap tersimpan; jual baru diblokir sampai jumlahnya turun.

## Konfigurasi

- `listing.expiry-hours`: masa listing aktif; bawaan 72 jam. Jam berjalan saat server offline; saat start atau membuka menu, listing yang sudah lewat batas masuk Unclaimed Items.
- `listing.tax-percent`: potongan hasil penjualan 0–100%; bawaan 0. Hasil bersih dibulatkan ke bawah dalam minor units. Pajak dicatat saat pembelian direservasi dan tidak berubah jika config direload sesudahnya. Pajak dibakar, bukan dikirim ke akun admin.
- `listing.min-price-minor` / `max-price-minor`: batas integer harga. Money 2 decimals: 100 minor = 1.00 Money. Points 0 decimals: 100 minor = 100 Points. Batas maksimum config 1,000,000,000,000 minor units.
- `listing.blocked-materials`: blacklist Material; bawaan kosong, sehingga semua item non-air dapat dijual.
- `capacity.default-pages`, `slots-per-page`, `levels`: kapasitas dan jumlah listing per halaman; ukuran halaman 1–45.
- `security.block-creative`: bawaan true; transaksi item/uang diblokir di Creative/Spectator. Browsing tetap tersedia.
- `security.max-item-bytes`: batas byte item serialized; bawaan 1 MiB. Item custom biasa termasuk shulker berisi item dapat dipertahankan, selama ukurannya di bawah batas.
- `categories`: kategori dan wildcard Material.
- `gui.titles`: judul GUI.
- `gui.buttons`: nama, ikon Material dan opsional `lore` untuk tombol menu utama/navigasi. Layout slot tetap.
- `messages`: pesan bahasa Indonesia, warna `&`, dan placeholder bawaan.

Jalankan `/pmarket reload` sesudah edit. Reload menutup GUI dan membatalkan prompt chat aktif. Reload tidak membuka market yang dijeda karena kegagalan penyimpanan; perbaiki storage dan restart.

**Currency ID, type dan decimals menjadi tetap setelah dipakai.** Plugin menyimpan definisinya di database dan menolak perubahan yang bisa menafsirkan ulang harga/saldo lama. Jangan hapus currency yang masih mempunyai escrow/listing/payout. Backend Vault dipilih oleh ServicesManager dari economy provider aktif. Jangan mengganti economy provider ke penyimpan saldo berbeda saat ada transaksi tertunda.

## Integrasi pembayaran lain

Backend tersedia: `VAULT`, `PLAYERPOINTS`, `SERVICE`. Tambahkan beberapa currency sekaligus, maksimal 20. Vault menjembatani plugin economy yang benar-benar menyediakan layanan Economy; plugin yang hanya memakai command untuk mengubah saldo belum tentu bisa langsung dipakai.

Contoh points:

```yaml
currencies:
  points:
    enabled: true
    type: PLAYERPOINTS
    label: Points
    decimals: 0
```

Backend custom mengimplementasikan `id.hellnah.market.MarketCurrency` dan mendaftarkan service ke Bukkit ServicesManager. Add-on perlu `depend: [HellNah-PlayerMarketGui]` dan mendaftarkan service saat add-on aktif. Currency SERVICE dapat dikonfigurasi sejak awal; provider dicari saat transaksi. Jika belum tersedia, pembayaran/penarikan ditolak tanpa mengubah saldo market. Dependensi compile add-on memakai source/JAR ini sebagai provided.

```java
Bukkit.getServicesManager().register(MarketCurrency.class, adapter, this, ServicePriority.Normal);
```

`id()` harus sama dengan ID config. `withdraw`/`deposit` menerima integer minor units. Return false hanya jika saldo **tidak berubah**. Jika hasil tidak diketahui, throw exception; plugin mengarantina transaksi. Adapter wajib memastikan cek saldo dan perubahan saldo atomik di provider. Payout PlayerPoints tidak melebihi Integer.MAX_VALUE dalam satu penarikan; jika akumulasi lebih besar, perlu adapter SERVICE yang mendukung nominal besar. Versi ini tidak memakai command console pembayaran karena sukses/rollback-nya tidak dapat diverifikasi.

## Penyimpanan dan pemulihan

Data market berada di tabel MySQL berprefix. Tabel memakai InnoDB, transaksi SQL, state bersyarat dan audit. Metadata pencarian terpisah dari payload item. Payload satu halaman GUI diambil sekaligus dalam satu query, agar tidak membuat satu query terpisah per item. Data player disimpan setelah item dipindahkan.

Koneksi khusus tidak melakukan auto-reconnect dan tidak mengulang operasi secara otomatis. Kegagalan SQL menjeda market agar transaksi yang hasilnya belum pasti tidak dicoba lagi. Perbaiki database dan restart, lalu periksa pending operations. Koneksi ke database masih berjalan di main thread dengan timeout; gunakan database dekat server dan uji latency/beban. Pooling dan pemrosesan database penuh secara async belum diterapkan pada versi ini.

**Database market, file player Minecraft dan economy eksternal tidak mempunyai satu transaksi atomik bersama.** Kalau server mati atau provider melempar exception tepat saat berpindah, hasil bisa belum pasti. Plugin memilih mengunci operasi tersebut (`*_PREP`) dan tidak mengulang otomatis. Ini mengurangi risiko duplikasi tetapi beberapa item/uang mungkin perlu pemulihan manual. Jangan mengklaim tahan semua hard crash atau 100% bebas bug.

Gunakan `/pmarket pending`, cek log server dan log economy, periksa inventory pemain yang bersangkutan dan backup. Sebelum resolve, minta pemain keluar agar isi inventory dapat diperiksa secara konsisten. Resolusi hanya untuk admin tepercaya dan dicatat di audit.

| State | Verifikasi sebelum commit | Makna commit | Kapan abort boleh dipakai |
|---|---|---|---|
| `LIST_PREP` | Item benar-benar sudah hilang dari inventory penjual | Listing menjadi aktif | Item masih utuh di inventory penjual; listing dibuang |
| `BUY_PREP` | Uang benar-benar terpotong tepat sekali | Item masuk unclaimed pembeli, saldo bersih dikreditkan penjual sekali | Uang tidak terpotong, atau sudah direfund manual dan diverifikasi; listing kembali aktif |
| `CLAIM_PREP` | Item benar-benar sudah diterima di inventory holder | Claim dianggap selesai | Item belum diterima atau salinan yang terlanjur diterima sudah dihapus dan diverifikasi; item dapat diambil ulang |
| `PAY_PREP` | Deposit ke economy benar-benar sudah terjadi tepat sekali | Payout dianggap selesai | Deposit belum terjadi atau deposit sudah dibalikkan dan diverifikasi; saldo internal dikembalikan |

Contoh setelah verifikasi:

```text
/pmarket resolve 123 commit
/pmarket resolve 123 abort
/pmarket resolve payout:7 commit
```

ID payout berbeda namespace dengan listing. Jangan asal resolve, jangan resolve dua cabang sekaligus, dan jangan menghapus database untuk memperbaiki error. Jika provider sudah mengubah uang tetapi hasilnya ambigu, abort tanpa refund akan menghasilkan uang/item ganda. Admin yang memegang permission resolve memang dapat melakukan pemulihan yang salah; permission ini jangan diberikan ke pemain biasa.

Backup: stop server, lalu ekspor tabel market melalui panel MySQL dan backup data player/economy pada waktu yang sama. Simpan config runtime di tempat pribadi. Jangan menjalankan dua server dengan database/prefix sama. Rollback database tanpa menyelaraskan inventory player dan economy dapat menyebabkan duplikasi.

## Validasi dan batas pengujian

Source MySQL versi 1.1.0 tidak dibuild dan pengujian Java/MySQL belum dijalankan pada sesi pembuatannya, sesuai permintaan agar kamu build sendiri. Pemeriksaan lokal hanya mencakup struktur project, syntax Java melalui parser, YAML, XML pom, dependency scope, versi, dan isi paket tanpa binary. Laporan 15 tes SQLite versi lama tidak berlaku untuk backend MySQL ini dan tidak disertakan.

Test unit DatabaseSettingsTest memeriksa validasi prefix/URL dan redaksi credential. Test integrasi StoreIT memeriksa escrow dan recovery memakai MySQL sungguhan, termasuk advisory lock dan pengambilan payload halaman. Tes ini baru akan dijalankan oleh Maven dengan profil mysql-it atau workflow GitHub. GUI dan economy eksternal tetap memerlukan uji server dengan dua akun.

Checklist server percobaan:

1. Akun A jual stack dan item custom; cek item hilang dari tangan dan tetap utuh di GUI.
2. Akun B beli, cek saldo hanya terpotong sekali; akun C mencoba membeli listing sama harus gagal.
3. B claim dengan inventory penuh lalu kosongkan satu slot; item diterima dengan metadata semula.
4. A tarik saldo, cek saldo internal habis dan provider bertambah sekali.
5. A batalkan listing dan klaim; B tidak dapat membatalkan listing A.
6. Coba permission 1 dan 2 halaman; listing ke-46 ditolak pada kapasitas 45.
7. Cek Browse GLASS menyatukan jenis item, lalu menampilkan penjual berbeda saat diklik. Cek search, filter, halaman dan Back.
8. Stop/start normal; listing, saldo dan unclaimed tetap ada. Untuk uji expiry, set 1 jam di test server.
9. Cek Creative, shift-click, number key, double-click dan drag tidak memindahkan item GUI.
10. Jangan menguji hard crash pada server produksi.

Tidak ada updater, telemetry, webhook, remote command, lisensi online atau command tersembunyi. Source lengkap disertakan agar dapat diaudit. Driver MySQL dimuat terpisah oleh Paper; skin kepala memakai fasilitas Bukkit/Paper normal.

## Build sendiri dan GitHub

Ekstrak ZIP, lalu upload isi folder project yang berisi pom.xml ke root repository GitHub. Sertakan src, README.md, PANDUAN.md, .gitignore, .gitattributes dan .github/workflows/build.yml. Jangan upload folder target, JAR, credentials, database dump atau config runtime server.

Gunakan JDK 21 dan Maven 3.9+:

```text
mvn clean package
```

Output: target/HellNah-PlayerMarketGui-1.1.0.jar. Paper API dan MySQL Connector/J menggunakan scope provided; tidak ada plugin shade atau SQLite dependency. Jika versi Connector/J diubah, sesuaikan pom.xml **dan** plugin.yml libraries.

Menjalankan integration test di database khusus uji:

```bash
export PMARKET_TEST_HOST=127.0.0.1
export PMARKET_TEST_PORT=3306
export PMARKET_TEST_DATABASE=hellnah_market_test
export PMARKET_TEST_USER=market_test
export PMARKET_TEST_PASSWORD='password database uji'
mvn -Pmysql-it clean verify
```

Di PowerShell, set environment dengan `$env:PMARKET_TEST_DATABASE = "hellnah_market_test"` dan variable lainnya dengan cara sama, lalu jalankan command Maven. Profil mysql-it gagal jika nama database uji belum diisi; tidak melewatkan pengujian secara diam-diam. Tiap test membuat prefix acak sendiri dan membersihkan tabel uji setelahnya, tidak menghapus database. Gunakan database terpisah dari produksi.

Workflow GitHub menjalankan MySQL 8.4 container sementara, JDK 21, unit test, integration test dan build; lalu menyimpan JAR di Actions Artifacts. Password dalam workflow hanya untuk container CI sementara, bukan password panel VPS. Workflow tidak mempublish release atau memasang plugin ke server.

Referensi implementasi:

- Paper runtime libraries: https://docs.papermc.io/paper/dev/plugin-yml/
- Connector/J security properties: https://dev.mysql.com/doc/connector-j/en/connector-j-connp-props-security.html
- Connector/J 9.7 compatibility: https://docs.oracle.com/cd/E17952_01/connector-j-en/connector-j-versions.html
