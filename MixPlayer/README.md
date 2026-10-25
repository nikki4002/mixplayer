# MixPlayer

Player de música Android (Kotlin + Jetpack Compose + Media3) com a função **Mix**, inspirada no Blend do Spotify.

Feito para o Redmi 23129RAA4G (Snapdragon 685, Android 8+).

## O que faz

- Lista e toca as músicas que estão no celular, com busca, toque em segundo plano e notificação.
- Conta quantas vezes você ouviu cada música e deixa favoritar com ★.
- **Mix**: você compartilha seu código (pelo WhatsApp, por exemplo), o amigo cola no app dele e vice-versa. O app mostra a compatibilidade e monta uma playlist alternando suas músicas e as do amigo. Músicas que os dois ouvem vêm primeiro.
- Cada música do Mix mostra de quem ela veio (você, o amigo ou os dois). As que não existem no seu celular aparecem apagadas e não tocam.
- Para atualizar o Mix, peça um código novo ao amigo. "Sair do Mix" apaga o Mix.

Compatibilidade: 60% artistas em comum + 40% músicas em comum, entre as 60 mais ouvidas de cada um.

Tudo funciona offline e sem servidor: o código carrega seu perfil de gosto (título, artista e pontuação das suas top músicas), compactado.

## Como gerar o APK (sem Android Studio)

1. Crie um repositório no GitHub e suba todo o conteúdo desta pasta.
2. Abra a aba **Actions**, escolha **Build APK** e clique em **Run workflow** (ou faça um push na branch `main`).
3. Quando terminar (uns 5 min), baixe o artefato **MixPlayer-apk**, extraia o `.apk` e instale no celular.
4. O Android vai pedir para permitir "instalar apps desconhecidos" para o app que você usou para abrir o arquivo.

## Como gerar com Android Studio

Abra a pasta no Android Studio, espere o Gradle sincronizar e use **Build > Build APK(s)**. Ou, no terminal: `./gradlew assembleRelease`. O APK fica em `app/build/outputs/apk/release/`.

## Estrutura

- `MainActivity.kt`: telas (Músicas e Mix) e controle do player
- `PlaybackService.kt`: reprodução em segundo plano e contagem de reproduções
- `Mix.kt`: código de perfil, compatibilidade e montagem do Mix
- `Store.kt`: histórico, favoritos, nome e código do amigo
- `MusicRepository.kt`: lê as músicas do celular
