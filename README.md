# Jarvis

Assistente de voz para Android em português brasileiro. O app ouve comandos de
voz, interpreta frases simples e executa ações no dispositivo — abrir aplicativos,
pesquisar na web, definir timers e informar as horas.

Este repositório estava em estágio de esboço; esta versão reorganiza o projeto,
corrige a compilação e entrega um MVP funcional.

## Funcionalidades

- **Comando por voz** — toque em “Ouvir comando” e fale em pt-BR.
- **Abrir aplicativos** — WhatsApp, Spotify, Chrome, YouTube e Instagram
  (com fallback para a Play Store quando o app não está instalado).
- **Pesquisa na web** — Google e YouTube.
- **Previsão do tempo** — abre a busca do Google para a consulta.
- **Timers** — “timer de 5 minutos”, “timer de 30 segundos”, “daqui a 2 horas”.
- **Horas** — “que horas são?”.
- **Escuta contínua (experimental)** — serviço em foreground aguardando a
  palavra de ativação “Jarvis” e abrindo o assistente quando ela é detectada.

## Estrutura

```
app/src/main/java/com/zmove/jarvis/
├── MainActivity.kt        # UI, permissões e captura de um comando
├── JarvisService.kt       # serviço de escuta contínua (foreground)
├── CommandProcessor.kt    # interpretação das frases (puro Kotlin, testável)
├── Speech.kt              # normalização de texto e intents de reconhecimento
├── TimerScheduler.kt      # agendamento de timers com AlarmManager
└── TimerReceiver.kt       # notificação quando o timer dispara
```

O `CommandProcessor` não depende do Android e é coberto por testes unitários.

## Requisitos

- Android Studio (Ladybug ou mais recente) ou JDK 17+.
- Android SDK 35 instalado.
- Dispositivo ou emulador com Google app / serviço de reconhecimento de voz.

## Como compilar

```bash
./gradlew assembleDebug
```

O APK é gerado em `app/build/outputs/apk/debug/app-debug.apk`.

Para rodar os testes unitários:

```bash
./gradlew test
```

## Permissões

| Permissão | Uso |
| --- | --- |
| `RECORD_AUDIO` | Captura de voz para os comandos |
| `POST_NOTIFICATIONS` | Notificação do serviço e avisos de timer |
| `FOREGROUND_SERVICE` / `FOREGROUND_SERVICE_MICROPHONE` | Escuta contínua |
| `INTERNET` | Reconhecimento de fala e pesquisas |

## Limitações conhecidas

- O reconhecimento usa o `SpeechRecognizer` do sistema (Google). Em aparelhos
  sem o serviço, a captura de voz não funciona.
- A escuta contínua mantém o microfone ativo e consome bateria. O Android pode
  bloquear a abertura automática da tela a partir do segundo plano; nesse caso,
  toque na notificação fixa.
- Não há integração com LLM ainda. Frases fora dos comandos mapeados retornam
  “ainda não sei fazer isso”.

## Roadmap

- [ ] Wake word local com [Picovoice Porcupine](https://picovoice.ai/) (sem
      depender do serviço do Google e com menor consumo).
- [ ] Integração com Gemini para comandos livres e respostas faladas (TTS).
- [ ] Mais comandos: alarmes, ligações, mensagens, controle de mídia.
- [ ] Testes instrumentados e CI (GitHub Actions).
- [ ] Internacionalização (pt-BR padrão, en como alternativa).

## Licença

Projeto privado da Zmove Digital. Todos os direitos reservados.
