# Virtual Workspace

Aplicação Android nativa (Kotlin + Jetpack Compose) que funciona como um **Workspace Virtual Inteligente**: organiza ficheiros por **referências** (como playlists de música) sem os mover fisicamente, com **consolidação física opcional** em background.

## Conceito

- **Workspaces** (`WorkspaceRoot`): contextos independentes (Trabalho, Universidade, Pessoal)
- **Árvore virtual** (`VirtualNode`): pastas e referências organizadas como o utilizador quiser — mover/renomear é instantâneo (só metadados)
- **Storage Objects** (`StorageObject`): ficheiros reais em `APP_PRIVATE`, `SAF_TREE` ou `MEDIASTORE`
- O mesmo ficheiro pode aparecer em várias pastas virtuais sem duplicação

## Funcionalidades (MVP)

| Funcionalidade | Estado |
|---|---|
| Múltiplos workspaces | Implementado |
| Navegação hierárquica com breadcrumbs (RF01) | Implementado |
| CRUD de pastas virtuais, instantâneo (RF02) | Implementado |
| Referências a ficheiros via SAF (RF03) | Implementado |
| Consolidação física em background com journal e compensação (RF04, RNF03) | Implementado |
| Remoção de referência sem apagar ficheiro (RF05) | Implementado |
| Lixeira com retenção de 30 dias (RF06) | Implementado |
| Document Provider nativo (RF07) | Implementado |
| Share Target — receção de ficheiros (RF08) | Implementado |
| Mapeamento de pastas físicas via SAF (RF09) | Implementado |
| Favoritos / fixar na raiz (RF10) | Implementado |
| Pesquisa com localização física (RF11) | Implementado |
| Journal de operações (RF12) | Implementado |
| Validação periódica de permissões SAF (RF13) | Implementado |
| Propriedades detalhadas (RF14) | Implementado |
| Smart folders | Preparado no modelo (futuro) |

## Arquitetura

Clean Architecture (MVVM + Use Cases + Repository), 100% offline, sem permissão de internet.

```
presentation/   Compose UI + ViewModels (browser, pesquisa, lixeira, definições, importação)
domain/         Entidades, interfaces de repositório, use cases
data/           Room (metadados), implementações de repositórios, gateway físico (SAF/privado/MediaStore)
infrastructure/ DocumentsProvider, Share Target, WorkManager workers, notificações
di/             Módulos Hilt (todos os singletons via @Singleton + @Inject)
```

Pontos-chave:

- **Materialized path** (`virtual_path`) com batch update numa transação Room para mover subárvores inteiras instantaneamente
- **Operation Journal**: rastreabilidade, retomada e compensação de operações físicas (não atomicidade física total — o filesystem não participa em transações)
- **Validação de referências por custo**: URI → lastModified → size → checksum SHA-256 (lazy)
- **WorkManager** com restrição de bateria para consolidação, validação de permissões e limpeza da lixeira

## Stack

- Kotlin 2.0, Jetpack Compose (Material 3), Navigation Compose
- Room, Hilt (+ hilt-work), WorkManager, DocumentFile/SAF, kotlinx.serialization
- minSdk 26 (Android 8.0), targetSdk 35

## Build

Requisitos: JDK 17 e Android SDK (platform 35).

```bash
./gradlew assembleDebug
```

O APK fica em `app/build/outputs/apk/debug/`. Distribuição prevista via GitHub Releases (não Play Store no MVP).

## Verificação

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Os schemas Room são exportados para `app/schemas/` e devem ser versionados para permitir testes de migração nas próximas versões da base de dados.

## Permissões

- Sem `INTERNET`, sem `MANAGE_EXTERNAL_STORAGE`, sem `QUERY_ALL_PACKAGES`
- `POST_NOTIFICATIONS` para progresso de operações em background
- Acesso a pastas do utilizador exclusivamente via SAF (permissões persistentes geridas na app)
