# WhiteDragon

Kişisel CloudStream eklenti reposu

## CloudStream'e ekleme

Uygulamada **Ayarlar → Eklentiler → Depo Ekle** yoluna git ve şu linki yapıştır:

```
https://raw.githubusercontent.com/darknesslord19/Deneme/main/repo.json
```

## Bu depoyu kullanmaya başlama

1. Bu klasörü GitHub'da `Deneme` adıyla yeni bir repo olarak oluştur (repo oluştururken **Include all branches** kutusunu işaretleme, önemli değil — `builds` dalını CI otomatik oluşturur).
2. Tüm dosyaları push et.
3. Ayarlar → Actions → **Workflow permissions**'ı "Read and write permissions" yap (CI'nin `builds` dalına yazabilmesi için).
4. `main` dalına her push'ta `.github/workflows/build.yml` otomatik çalışır, eklentileri derler ve `builds` dalına `plugins.json` + `.cs3` dosyalarını yayınlar.
5. Her eklenti klasöründeki (`WhiteDragon`) `src/main/kotlin/.../{Ad}.kt` dosyasını kendi kaynağına göre doldur.

## Eklentiler

- **WhiteDragon** — Açıklama girilmedi (Movie, tr)

## Yerelde derleme

```
./gradlew ExampleProvider:make
```
üretilen `.cs3` dosyasını cihazına `adb` ile atıp CloudStream'de yükleyebilirsin, ya da `deployWithAdb` task'ını kullan.
