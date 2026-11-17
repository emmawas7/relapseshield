# Relapse Shield

Relapse Shield is a specialized mobile addiction recovery follow-up application designed to maintain a secure digital link between outpatient recovery patients and their assigned rehabilitation counselors. It facilitates daily patient self-reflections (mood, craving levels, triggers), automatically flags high-risk behavioral patterns for counselor intervention, and provides an immediate emergency panic and breathing support system.

## 1. Setup
- **Development Environment**: Android Studio Quail 4 (2026.1.4), Java, Empty Views Activity architecture.
- **SDK Configuration**: `minSdk 24` (Android 7.0), `targetSdk 37`. 
  - *Why minSdk 24*: `minSdk 24` covers over 99% of active Android devices worldwide while providing full native support for modern AndroidX, WorkManager, AlarmManager, and Firebase 35.1 libraries without needing legacy multidex overhead.
- **Backend Architecture**: Firebase Authentication (email/password) and Cloud Firestore NoSQL Database.
- **Test Environments**:
  - *Emulator*: Pixel 8 Pro (Android 14 / API 34, 1080x2400 resolution).
  - *Physical Device*: Samsung Galaxy A32 (Android 13 / API 33).
- **Security & Keys**: `google-services.json` is excluded from Git tracking (`.gitignore`). To run the project, place a valid `google-services.json` inside the `/app` module directory for package `com.example.relapseshield`.

## 2. What each milestone contains
- **Milestone 1**: Concept design, user personas, declarative XML layout construction, input validation, welcome feedback, and activity lifecycle logging on Login.
- **Milestone 2**: Navigation task flows, Intent data passing, counselor code linking, priority-sorted counselor dashboard, overflow menus, alert dialogs, native dialler/messaging integrations, and custom reusable list item layouts.
- **Milestone 3**: Cloud Firestore database integration (full CRUD), offline snapshot caching, role-based authorization, account/record deletion confirmation dialogs, and security risk controls.
- **Milestone 4**: REST API integration (`ResourcesActivity`), exact daily alarm capabilities (`AlarmManager` & `NotificationManager`), 10 comprehensive test cases, bug log, release build configuration, and technology stack comparison.

## 3. Users & Personas

| User | What they do |
|---|---|
| **Patient** | Registers using a counselor's unique code, records next-of-kin contact info, submits daily check-ins, accesses emergency breathing tools, and receives daily reminders. |
| **Counsellor** | Reviews linked patient requests, monitors a real-time red-flagged dashboard sorted by risk severity, manages custom keyword alert lists, logs contact actions, and marks patient check-ins reviewed. |

### Personas

#### Persona 1: Nabakooza Bridget Maria (Patient in Recovery)
- **Age**: 24 | **Situation**: Discharged from an outpatient addiction treatment program 3 weeks ago; currently living independently and working.
- **Goal**: Quickly complete a daily 1-minute reflection on mood and cravings without feeling overwhelmed, and have instant access to emergency support if cravings spike.
- **Frustration**: Forgetting daily reflection habits, struggling with sudden triggers, and fearing relapse without timely counselor support.

#### Persona 2: Dr. Emmanuel Wasswa (Rehabilitation Counselor)
- **Age**: 42 | **Situation**: Senior Addiction Counselor managing outpatient recovery clients alongside clinical rounds.
- **Goal**: Instantly identify which patients are experiencing severe mood drops, high cravings, or extended silence so he can intervene before a full relapse occurs.
- **Frustration**: Managing scattered paper notes and missing early warning signs because patients only reach out when a crisis has already occurred.

## 4. Screens & Layout Justification

| Screen | Layouts Used | Main Controls | Purpose |
|---|---|---|---|
| **Welcome** | `ScrollView`, `LinearLayout` | `Button` | App introduction, privacy trust statement, and main entry point to Login or Register. |
| **Login** | `ScrollView`, `ConstraintLayout` | `TextInputLayout`, `CheckBox`, `Button` | Authenticates users, validates inputs, and saves "Remember Me" credentials in preferences. |
| **Register** | `ScrollView`, `LinearLayout` | `RadioButton`, `Spinner`, `CheckBox`, `Button` | Form for registering as a Patient (with counselor code & kin info) or Counselor (generating a code). |
| **Connect with Counsellor** | `LinearLayout` | `TextInputLayout`, `Button` | Unlinked patient onboarding screen to enter a 6-character counselor code. |
| **Home (Patient)** | `LinearLayout`, `ListView` | `ArrayAdapter`, `Button`, `PopupMenu` | Patient home portal showing past check-ins, new check-in entry button, and emergency panic button. |
| **New Check-in** | `ScrollView`, `LinearLayout` | `Slider`, `RadioGroup`, `CheckBox`, `TextInputEditText` | Form to log daily mood (1–5), craving level, trigger state, and optional note scanned for flag words. |
| **Dashboard (Counsellor)** | `LinearLayout`, `ListView` | `ArrayAdapter`, `PopupMenu`, `AlertDialog` | Priority-sorted list displaying open urgent alerts, pending patient link requests, and red-flagged patients. |
| **Patient Details (Counsellor)** | `ScrollView`, `LinearLayout` | `Button` (Call, Message, Call Kin, Mark Reviewed) | Displays patient contact details, next of kin, check-in history, and options to contact or mark reviewed. |
| **Emergency Support** | `ScrollView`, `LinearLayout` | `CountDownTimer`, `Button` | Guided 1-minute breathing exercise, direct helpline dialler, and urgent alert trigger. |
| **Flag Words** | `ScrollView`, `LinearLayout`, `ListView` | `RadioGroup`, `TextInputEditText`, `Button` | Counselor management screen for adding and removing custom "watch" and "urgent" keyword triggers. |
| **Resources** | `ScrollView`, `ListView` | `ProgressBar`, `Button`, `ListView` | REST API screen fetching community rehabilitation centers and helplines via HTTP GET request. |
| **Daily Reminder** | `ScrollView`, `LinearLayout` | `SwitchCompat`, `Button`, `TimePickerDialog` | Configures exact daily alarm reminders (`AlarmManager`) and includes an instant test alarm trigger. |

### Welcome & Design Notes
- **Why these layouts**: `ScrollView` guarantees no content is cut off on smaller screens or landscape mode; `ConstraintLayout` optimizes flat view hierarchies for forms; `LinearLayout` provides predictable vertical scrolling flows.
- **Orientation & Screen Sizes**: Tested across portrait and landscape modes on multiple screen sizes. Layouts adjust dynamically using match_parent and weighted layouts (`layout_weight`).
- **Accessibility**: All touch targets meet minimum 48dp heights, text sizes use scale-independent pixels (`sp`), layout dimensions use density-independent pixels (`dp`), and proper contrast ratios with `contentDescription` tags are applied.
- **Trust Wording**: Headline and privacy promises explicitly clarify that personal health data is shared strictly with the user's assigned counselor and protected in Google Cloud.

## 5. Design Decisions
- **Welcome Screen Focus**: Designed as a trust-building landing page explaining what the app does before requesting credentials.
- **Firebase Synchronization**: Transitioned from local in-memory storage to Cloud Firestore so counselor dashboards reflect patient check-ins immediately.
- **Counselor Role Alignment**: Designed the counselor as an active co-user who receives real-time flags, moving recovery management from reactive to proactive.
- **Counselor Code Authorization**: Required patients to use a doctor's code to prevent unauthorized or accidental counselor linking.
- **Counselor-Only Communication Actions**: Call and Message buttons are restricted to counselor views to maintain clinical boundaries and prevent patient misuse.
- **Counselor Account Approval**: Counselor accounts start unapproved by default to ensure only verified medical professionals can access sensitive patient records.

## 6. Data Model (Cloud Firestore)

| Collection | Key Fields | Purpose |
|---|---|---|
| `users` | `role`, `name`, `phone`, `homeArea`, `kinName`, `kinRelation`, `kinPhone`, `counsellorId`, `code`, `approved`, `clientCount`, `lastReviewedAt`, `assignedAt` | Stores user profiles for both patients and counselors. |
| `counsellorCodes` | `counsellorId`, `counsellorName` | Index mapping 6-character counselor codes to counselor user IDs. |
| `requests` | `patientId`, `patientName`, `patientPhone`, `counsellorId`, `counsellorName`, `status`, `createdAt` | Holds pending patient requests awaiting counselor confirmation. |
| `checkins` | `userId`, `counsellorId`, `mood`, `cravings`, `triggered`, `note`, `urgentWords`, `watchWords`, `reviewed`, `createdAt`, `editedAt` | Daily patient check-in records. |
| `alerts` | `patientId`, `patientName`, `counsellorId`, `counsellorName`, `status`, `source`, `words`, `createdAt`, `handledAt` | Urgent alerts raised by emergency triggers or urgent keyword matches. |
| `accessLog` | `counsellorId`, `patientId`, `action`, `createdAt` | Audit log recording when a counselor views, calls, or messages a patient. |
| `flagWords` | `counsellorId`, `word`, `level` (`watch` / `urgent`) | Counselor-defined keyword trigger list. |

## 7. Validation Rules
- **Login**: Email required and valid syntax; password required and at least 6 characters. Returns a single unified error message for incorrect credentials to prevent account enumeration.
- **Register**: Full name >= 2 chars; valid email format; phone formatted as `07XXXXXXXX` or `+2567XXXXXXXX`; patients require home area, next-of-kin details, and a 6-char counselor code; counselors generate a code; password >= 8 chars with a letter and a number; matching confirm password; consent checkbox checked.
- **Check-in**: Mood slider (1–5); craving selection mandatory; note <= 500 characters.

## 8. Navigation & Intents
- **Explicit Intents**: Used for app-internal screen transitions (e.g., Login to Dashboard, Patient list to Patient details). Intent extras carry user IDs, names, roles, and check-in IDs.
- **Back Stack Management**: `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK` is applied upon login/logout so the Back button leaves the app instead of cycling through authentication screens.
- **Implicit Intents**: `Intent.ACTION_DIAL` opens the phone's native dialler with pre-filled numbers (without requesting direct call permissions); `Intent.ACTION_SENDTO` (`smsto:`) opens the phone's SMS app with a pre-filled greeting.

## 9. Security & Privacy Controls

| Risk Identified | Control Applied | Location in Code |
|---|---|---|
| **Unauthorized data access** | Firestore Security Rules ensure patients read/write only their own records; counselors read only linked client records. | Cloud Firestore Rules |
| **Fake counselor registration** | Counselor accounts start unapproved (`approved: false`) and require manual admin authorization before viewing data. | `RegisterActivity.java`, `DashboardActivity.java` |
| **Self-role manipulation** | Firestore Security Rules refuse client updates to `role`, `approved`, or `counsellorId` fields. | Cloud Firestore Rules |
| **Counselor client overload** | Client limit capped at 10 patients per counselor, enforced via atomic Firestore transaction. | `DashboardActivity.java` (`accept()`) |
| **Counselor contact misuse** | Every view, call, or message action is logged with timestamp accountability. | `PatientActivity.java` (`logAccess()`) |
| **Password exposure** | Password authentication managed exclusively by Firebase Authentication; passwords never saved in plain text or local DB. | `LoginActivity.java`, `RegisterActivity.java` |
| **Account enumeration attack** | Identical error message displayed for both invalid email and invalid password. | `LoginActivity.java` |
| **Exposed API / Service Keys** | `google-services.json` excluded from Git version control via `.gitignore`. | `.gitignore` |

- **Data Privacy Statement**: The app collects minimal personal data (Name, Phone, Next of Kin, Daily Mood/Cravings). Data is transmitted securely over TLS to Google Cloud Firestore and accessed solely by the user's verified counselor.

## 10. Learning Log

| Topic | Sources Used | What I Learned | One Problem I Solved |
|---|---|---|---|
| **Event Handling** | Course eBook, Android Docs | How lambda listeners capture click, slider, radio, and check events and validate inputs declaratively. | Fixed form submissions executing before validation by disabling save buttons until validation passes. |
| **Activity Lifecycle** | Android Developer Guide | How `onCreate`, `onStart`, `onResume`, `onPause`, `onStop`, `onDestroy` manage UI state during interruptions. | Fixed form state loss during screen rotation by storing state keys in `onSaveInstanceState` and using `savedInstanceState == null` checks. |
| **Navigation & Intents** | Android API Reference | Passing primitive and string extras between Activities and managing the back stack using Intent flags. | Solved back-button navigation loops between Login and Register by calling `finish()` on transition. |
| **Lists, Menus & Dialogs** | Material Design Guidelines | Building dynamic lists with `ArrayAdapter` and showing rich options via `PopupMenu` and `AlertDialog`. | Solved list item click crashes by checking bounds against underlying document ID lists. |
| **Firebase Auth & Firestore** | Firebase Android Documentation | Authenticating users, performing `WriteBatch` operations, and querying collections asynchronously on background threads. | Fixed unreviewed check-ins remaining red-flagged by bundling check-in, alert, and review updates into a single atomic `WriteBatch`. |
| **Security Rules & Permissions** | Android Security Best Practices | Implementing runtime permissions (`POST_NOTIFICATIONS`), intent filters, and Firestore Security Rules. | Fixed permission crash on Android 13+ by integrating `ActivityResultContracts.RequestPermission()`. |

- **Real Build Problems Solved**:
  1. *App Theme Styling*: Resolved app staying purple by aligning custom style inheritance with Material 3 components in `themes.xml`.
  2. *Duplicate Mipmap Resources*: Fixed a Gradle build failure (`Duplicate resources`) by removing redundant `.png` files when `.webp` launcher icons were generated.
  3. *Permission Failure on Patient Review*: Fixed "Could not load patient" errors during review actions by replacing forbidden updates on `users` documents with batch updates to `checkins` and `alerts`.

## 11. Lifecycle Observations

Observed state transitions logged on `LoginActivity`:

| User Action | Callbacks Executed (In Order) | System Behavior / Explanation |
|---|---|---|
| **Launch App** | `onCreate()` -> `onStart()` -> `onResume()` | Activity initializes view binding, sets up listeners, and becomes visible and interactive. |
| **Press Home Button** | `onPause()` -> `onStop()` | Activity is no longer visible; UI state is saved in memory while background processes pause. |
| **Return to App** | `onRestart()` -> `onStart()` -> `onResume()` | Activity comes back to the foreground and resumes interactive state without re-inflating views. |
| **Rotate Screen** | `onPause()` -> `onStop()` -> `onDestroy()` -> `onCreate()` -> `onStart()` -> `onResume()` | Activity is destroyed and recreated to adapt layouts to new orientation; input state restored via `savedInstanceState`. |
| **Tap Login / Navigate Away** | `onPause()` -> `onStop()` -> `onDestroy()` (if `finish()` called) | Activity finishes and is removed from the task back stack. |

## 12. Test Log & Bug Log

### Test Cases (Milestone 4)

| Test # | Focus Area | Inputs / Conditions | Expected Result | Actual Result | Pass/Fail |
|---|---|---|---|---|---|
| **TC-01** | Login Validation | Blank email and password | "Email is required" and "Password is required" inline errors displayed | As expected | **PASS** |
| **TC-02** | Login Auth | Unregistered email or wrong password | Single error: "Incorrect email or password" displayed | As expected | **PASS** |
| **TC-03** | Counselor Linking | Patient enters invalid counselor code | Inline error: "Invalid code" displayed; no request created | As expected | **PASS** |
| **TC-04** | Check-in Form | Cravings left unselected | Inline error prompting user to select a craving level | As expected | **PASS** |
| **TC-05** | Keyword Flagging | Patient enters note containing "urgent" flag word | Check-in saved, urgent alert document created in Firestore, emergency help dialog shown | As expected | **PASS** |
| **TC-06** | Daily Alarm | User toggles "Remind me every day" switch ON | Exact alarm scheduled with `AlarmManager`, toast confirmation shown | As expected | **PASS** |
| **TC-07** | Instant Test Alarm | User taps "Send a test reminder now" | High-priority notification with alarm ringtone and vibration triggers immediately | As expected | **PASS** |
| **TC-08** | REST API Connectivity | User opens Resources screen while online | `ResourcesActivity` executes HTTP GET request to Firestore REST endpoint, parses JSON, and displays list cards | As expected | **PASS** |
| **TC-09** | REST API Offline | User opens Resources screen in Airplane Mode | `ResourcesActivity` catches `UnknownHostException` and displays user-friendly "No internet connection" message with Retry button | As expected | **PASS** |
| **TC-10** | Delete Confirmation | Patient taps Delete on a check-in item | `AlertDialog` pops up asking for confirmation before deleting record from Firestore | As expected | **PASS** |

### Bug Log
- **Defect 1 (Critical)**: *Red-flagged patients remaining flagged after review*. **Fix**: Updated `markReviewed()` in `PatientActivity.java` to atomically update `checkins` (`reviewed: true`) and `alerts` (`status: "handled"`), and updated `FlagRules.reasons()` to clear silence flags once reviewed.
- **Defect 2 (Medium)**: *Refresh button showing toast without fetching fresh data*. **Fix**: Updated `loadAll()` in `DashboardActivity.java` to fetch Firestore queries using `Source.SERVER` with offline fallbacks.
- **Defect 3 (Medium)**: *Counselors briefly flashing patient screen on login*. **Fix**: Updated `LoginActivity.java` and `WelcomeActivity.java` to check the cached role upfront and open `DashboardActivity.class` directly.

## 13. Technology Stack Comparison (Milestone 4)

### Native Android (Java) vs. Cross-Platform Frameworks (Flutter / React Native)

#### 1. Performance & Execution Efficiency
Native Android developed in Java/Kotlin compiles directly into DEX bytecode that executes natively on the Android ART (Android Runtime) virtual machine. This provides optimal CPU/GPU rendering speeds, low memory overhead, and 60 FPS UI performance. In contrast, cross-platform frameworks like React Native rely on a JavaScript bridge to serialize layout and event calls, which can introduce micro-stuttering during complex list scrolling or animations. While Flutter compiles natively to C/C++ binaries using Skia/Impeller graphics, its initial bundle engine adds 10–15 MB to the baseline APK size.

#### 2. Access to Native Platform APIs & Device Hardware
In Native Android, accessing platform-level features like `AlarmManager`, `NotificationChannel`, `BroadcastReceiver`, `SharedPreferences`, or runtime permissions (`POST_NOTIFICATIONS`) requires no third-party wrappers and carries zero risk of library deprecation. In cross-platform development, developers must rely on community-maintained plugins (e.g., `flutter_local_notifications`, `workmanager`). If a community plugin is outdated or incompatible with the target Android SDK version (e.g., Android 14 exact alarm restrictions), native Java/Kotlin code bridges must still be written manually.

#### 3. Development Cost, Code Reuse & Maintenance
The main trade-off of Native Android is that code cannot be reused on iOS, effectively doubling the engineering effort if targeting both platforms. Cross-platform frameworks like Flutter or React Native allow 80–90% code reuse across iOS and Android, drastically reducing time-to-market for startup MVPs. However, for applications requiring high security, exact background scheduling, and deep system API integration (such as health follow-up and emergency alert systems), Native Android provides superior stability, complete SDK compliance, and simpler long-term maintenance.

## 14. Known Limitations
- Counselor account approval is currently toggled manually in the Firebase Console (`approved: true`).
- Push notifications are delivered locally via `AlarmManager` and `NotificationManager`; remote FCM server pushes are not implemented.
- Patient health records and counselor data are stored securely in Google Cloud Firestore.
- Flag keyword matching rules execute client-side during check-in submission.

## 15. AI Tools Used
- **Claude (Anthropic)**: Assisted in writing initial layout definitions and Firestore query structures.
- **Gemini (Google AI Plugin in Android Studio)**: Assisted in debugging Firestore batch permissions, implementing exact `AlarmManager` reminders, configuring `Source.SERVER` queries, fixing adaptive launcher icons, and organizing documentation.
