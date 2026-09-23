#!/usr/bin/env python3
"""
Compiles and strictly validates Google Play Store listings across all locales.
Ensures:
- title <= 30 chars
- shortDescription <= 80 chars
- fullDescription <= 4000 chars
Saves result to play-store-assets/listings.json.
"""

import json
import os

BASE_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS_DIR = os.path.join(BASE_DIR, "play-store-assets")

LISTINGS = {
    "en-US": {
        "title": "TipsyBuddy: Drink & BAC Track",
        "shortDescription": "Track drinks, estimate BAC & sober time, share live location & get home safe.",
        "fullDescription": """Party smart. Stay safe. Feel better tomorrow.

TipsyBuddy is your fun, friendly night-out companion. Keep track of your drinks, see an estimated BAC gauge and sober countdown, share your live location with friends, and get home safely with 1-tap rides.

Whether you're celebrating with a crew, enjoying dinner, or pacing yourself for tomorrow, TipsyBuddy helps you stay mindful without killing the vibe.

📊 Know Where You Stand
• Real-time blood alcohol content (BAC) estimator based on your profile
• Sober countdown timer shows roughly when you'll feel back to baseline
• Color-coded safety zones (Sober, Buzzed, Tipsy, Elevated) keep you in control

🍺 1-Tap Quick Drink Logging
• One-tap quick buttons for Beer, Wine, Cocktails, Shots, and Water
• Running drink counter and spending tab so you never get surprised at the end of the night
• Add custom drinks with customizable ABV, volume, and cost

💧 Stay Hydrated, Skip the Hangover
• Smart water reminders nudge you between alcoholic drinks
• Next-day hangover risk score with tailored recovery advice
• Electrolyte and hydration tips before you go to sleep

📍 Share Your Night Live
• Send friends a live map tracking link lasting from 30 minutes up to 4 hours
• Friends see your live location, venue check-in, and phone battery level
• Zero friction for friends: works in any browser on any phone or PC — no app download or sign-up needed!

🚕 Safe Rides & Emergency Dialing
• 1-tap buttons summon Uber or Lyft directly to your current location
• Instant speed dial for your trusted emergency contact or local emergency services
• Easy address copy to tell your designated driver or cab

📅 Look Back & Spot Trends
• Visual monthly calendar highlights light, moderate, heavy, and sober days
• Track your sober streaks and monthly nightlife spending
• Monitor your habits over time with private on-device statistics

⌚ Wear OS Smartwatch Companion
• Check your BAC estimate and sober countdown right on your wrist
• Quick-add drinks and check in to venues with a single tap
• Monitor steps and heart rate synced through your smartwatch sensors

🔒 Private by Design
• Your drinks, spending, and health data stay private on your device
• Live location sharing is 100% opt-in, only accessible via the unique link you share, and expires automatically
• No ads tracking your private personal life

⚠️ Important Safety Notice
TipsyBuddy provides estimated calculations based on standard physiological formulas (Widmark formula) for informational and harm-reduction purposes only. Metabolic rates vary greatly based on food intake, hydration, medications, fatigue, and individual biology. Never drink and drive. Always use a designated driver, taxi, or rideshare service. If you or someone around you requires urgent medical care, call your local emergency services immediately.

---
Privacy Policy: https://tipsybuddy.web.app/privacy.html
Terms of Service: https://tipsybuddy.web.app/terms.html
Live Companion Tracker: https://tipsybuddy.web.app"""
    },
    "es-419": {
        "title": "TipsyBuddy: Control Alcohol",
        "shortDescription": "Registra bebidas, estima alcohol, comparte ubicación y llega seguro a casa.",
        "fullDescription": """Sal de fiesta inteligente. Cuídate. Siéntete bien mañana.

TipsyBuddy es tu compañero amigable para salir de noche. Lleva el control de tus bebidas, revisa tu nivel estimado de alcohol y cuenta regresiva de sobriedad, comparte tu ubicación en vivo con amigos y regresa seguro con viajes en 1 toque.

Tanto si estás de fiesta con amigos como si disfrutas de una cena tranquila, TipsyBuddy te ayuda a mantener el control sin arruinar la diversión.

📊 Conoce tu estado en tiempo real
• Estimador de alcohol en sangre (BAC) basado en tu perfil
• Temporizador de sobriedad que indica cuándo volverás a tu estado normal
• Zonas de seguridad por colores (Sobrio, Alegre, Entonado, Elevado)

🍺 Registro de bebidas en 1 toque
• Botones rápidos para Cerveza, Vino, Cóctel, Shot y Agua
• Contador de bebidas y cuenta de gastos para evitar sorpresas al final de la noche
• Añade bebidas personalizadas con graduación alcohólica, volumen y precio

💧 Mantente hidratado, evita la resaca
• Recordatorios inteligentes para tomar agua entre tragos
• Índice de riesgo de resaca para el día siguiente con consejos de recuperación
• Consejos de electrolitos e hidratación antes de dormir

📍 Comparte tu noche en vivo
• Envía a tus amigos un enlace de mapa en vivo por 30 minutos hasta 4 horas
• Tus amigos ven tu ubicación en directo, el bar donde estás y el nivel de batería
• Sin complicaciones: ¡tus amigos lo abren en cualquier navegador sin instalar apps ni registrarse!

🚕 Viajes seguros y contactos de emergencia
• Botones de 1 toque para pedir Uber o Lyft directamente a tu ubicación
• Marcado rápido para tu contacto de confianza o emergencias
• Copia fácil de dirección para dársela a tu conductor designado o taxi

📅 Historial y calendario mensual
• Calendario visual que muestra días sobrios, moderados y de fiesta
• Sigue tus rachas sobrias y tu gasto mensual en salidas
• Estadísticas privadas guardadas en tu dispositivo

⌚ Compañero para Wear OS
• Consulta tu nivel estimado de alcohol y tiempo sobrio en tu muñeca
• Añade bebidas y haz check-in en locales con un solo toque

🔒 Privacidad total
• Tus datos de consumo, gastos y salud se quedan en tu teléfono
• Compartir ubicación es 100% opcional y expira automáticamente

⚠️ Aviso importante de seguridad
TipsyBuddy ofrece cálculos estimados con fines exclusivamente informativos. El metabolismo varía según la comida, hidratación, fatiga y biología individual. Nunca conduzcas bajo los efectos del alcohol. Utiliza siempre un conductor designado, taxi o app de transporte."""
    },
    "es-ES": {
        "title": "TipsyBuddy: Control Alcohol",
        "shortDescription": "Registra copas, estima alcoholemia, comparte ubicación y vuelve a casa seguro.",
        "fullDescription": """Sal de fiesta inteligente. Cuídate. Siéntete bien mañana.

TipsyBuddy es tu compañero ideal para salir de fiesta. Lleva la cuenta de tus copas, consulta tu tasa estimada de alcoholemia y cuenta atrás de sobriedad, comparte tu ubicación en directo con amigos y vuelve a casa seguro con viajes en 1 toque.

📊 Conoce tu estado en tiempo real
• Estimación del nivel de alcohol en sangre según tu perfil
• Cuenta atrás para volver a estar sobrio
• Zonas de color para controlar tu ritmo en todo momento

🍺 Registro de consumiciones en 1 toque
• Botones rápidos para Cerveza, Vino, Combinado, Chupito y Agua
• Control de gastos y número de bebidas acumuladas
• Crea bebidas personalizadas con volumen, graduación y coste

💧 Hidratación y prevención de resaca
• Avisos inteligentes para beber agua entre consumiciones
• Nivel de riesgo de resaca para mañana con consejos prácticos
• Consejos de recuperación antes de irte a dormir

📍 Comparte tu noche en directo
• Enlace de mapa en vivo de 30 minutos a 4 horas
• Tus amigos ven tu bar, ubicación y batería en cualquier navegador, sin instalar nada

🚕 Vuelve a casa seguro
• Pide Uber o Cabify en 1 toque hacia tu posición exacta
• Llamada directa a tu contacto de confianza o emergencias

📅 Historial y rachas
• Calendario mensual de consumo y gasto
• Registro seguro y privado en tu propio móvil

⚠️ Aviso de seguridad
Los datos mostrados son estimaciones basadas en fórmulas estándar con fin informativo. Nunca conduzcas si has bebido. Utiliza transporte público, taxi o conductor designado."""
    },
    "fr-FR": {
        "title": "TipsyBuddy: Suivi d'Alcool",
        "shortDescription": "Suivez vos verres, estimez l'alcoolémie, partagez votre position et rentrez sûr.",
        "fullDescription": """Faites la fête intelligemment. Restez en sécurité. Réveillez-vous en forme.

TipsyBuddy est votre compagnon de soirée bienveillant. Suivez vos verres en un geste, consultez votre taux d'alcoolémie estimé et le temps restant avant d'être sobre, partagez votre position en direct avec vos amis et rentrez chez vous en toute sécurité.

📊 Sachez où vous en êtes
• Estimation en temps réel de votre taux d'alcoolémie selon votre profil
• Compte à rebours indiquant l'heure approximative de retour à jeun
• Zones de sécurité par couleur pour garder le contrôle toute la nuit

🍺 Enregistrement des verres en 1 geste
• Boutons rapides pour Bière, Vin, Cocktail, Shot et Eau
• Compteur de verres et suivi des dépenses en temps réel
• Personnalisez vos boissons (degré d'alcool, volume, prix)

💧 Hydratation et réveil sans gueule de bois
• Rappels intelligents d'eau entre chaque consommation
• Score de risque de gueule de bois le lendemain et conseils de récupération
• Recommandations d'hydratation avant d'aller vous coucher

📍 Partagez votre soirée en direct
• Lien de carte en direct valable de 30 minutes à 4 heures
• Vos amis visualisent votre lieu, position et niveau de batterie
• Sans friction : fonctionne directement dans n'importe quel navigateur sans installation d'application !

🚕 Retour sécurisé en VTC & Urgences
• Accès rapide en 1 touche pour commander Uber ou taxi
• Appel instantané de votre contact de confiance ou des secours

📅 Calendrier et suivi personnel
• Visualisez vos soirées légères, modérées ou sobres au cours du mois
• Suivi des séries sans alcool et budget de sorties
• Données strictement privées stockées sur votre appareil

⚠️ Avis de sécurité important
TipsyBuddy fournit des estimations théoriques à titre purement indicatif et de réduction des risques. Le métabolisme varie selon l'alimentation, la fatigue et les personnes. Ne prenez jamais le volant après avoir bu. Utilisez un chauffeur désigné, un taxi ou les transports en commun."""
    },
    "de-DE": {
        "title": "TipsyBuddy: Promille & Drinks",
        "shortDescription": "Getränke tracken, Promille schätzen, Live-Standort teilen & sicher nach Hause.",
        "fullDescription": """Clever feiern. Sicher bleiben. Morgen fit aufwachen.

TipsyBuddy ist dein verlässlicher Begleiter für jeden Abend. Behalte deine Getränke im Blick, sieh deinen geschätzten Promillewert und den Nüchtern-Countdown, teile deinen Live-Standort mit Freunden und komm sicher nach Hause.

📊 Den Überblick behalten
• Echtzeit-Schätzung deines Blutalkoholgehalts (Promille) basierend auf deinem Profil
• Countdown-Timer, der anzeigt, wann du wieder nüchtern bist
• Farbcodierte Sicherheitsstufen für maximale Kontrolle

🍺 Getränke mit einem Tippen erfassen
• Schnellwahltasten für Bier, Wein, Cocktails, Shots und Wasser
• Getränkezähler und Ausgabenübersicht für die gesamte Nacht
• Eigene Getränke mit individuellem Alkoholgehalt und Preis anlegen

💧 Hydriert bleiben – Kater vermeiden
• Clevere Erinnerungen für Wasserpausen zwischen alkoholischen Drinks
• Kater-Risikobewertung für den nächsten Morgen mit Erholungstipps
• Wichtige Elektrolyt- und Hydrierungstipps vor dem Schlafen

📍 Live-Standort mit Freunden teilen
• Teile einen sicheren Live-Kartenlink für 30 Minuten bis zu 4 Stunden
• Freunde sehen deinen aktuellen Standort, Club/Bar und Akkustand
• Keine App-Installation für Freunde erforderlich – läuft in jedem Webbrowser!

🚕 Sicher nach Hause
• 1-Tipp-Aufruf für Uber, Taxi oder Fahrtdienst
• Schnellanruf für Notfallkontakte
• Adresse schnell kopieren für Fahrer

📅 Kalender & Ausgabenverlauf
• Monatskalender mit nüchternen, moderaten und Party-Tagen
• Nüchtern-Serien und Monatsbudget im Überblick
• 100 % private Speicherung auf deinem Smartphone

⚠️ Wichtiger Sicherheitshinweis
TipsyBuddy berechnet Richtwerte auf Basis wissenschaftlicher Formeln ausschließlich zu Informationszwecken. Alkoholabbau hängt von Ernährung, Schlaf und Körper ab. Niemals alkoholisiert fahren! Nutze stets ein Taxi, Fahrgemeinschaften oder den öffentlichen Nahverkehr."""
    },
    "pt-BR": {
        "title": "TipsyBuddy: Monitor Bebidas",
        "shortDescription": "Monitore bebidas, estime o teor alcoólico, compartilhe o local e vá seguro.",
        "fullDescription": """Curta a noite com inteligência. Fique seguro. Acorde bem amanhã.

O TipsyBuddy é o seu companheiro para saídas e noitadas. Monitore seus drinks, acompanhe a taxa estimada de álcool no sangue e a contagem regressiva para ficar sóbrio, compartilhe sua localização em tempo real com amigos e volte em segurança.

📊 Saiba como você está em tempo real
• Estimativa do teor alcoólico no sangue (BAC) calculada com base no seu perfil
• Cronômetro regressivo para a sobriedade
• Zonas coloridas de segurança para você manter o controle

🍺 Registro de bebidas em 1 toque
• Botões rápidos para Cerveja, Vinho, Coquetel, Dose e Água
• Contador de drinks e comanda de gastos para evitar surpresas na conta
• Crie bebidas personalizadas com teor alcoólico, volume e valor

💧 Hidrate-se e evite a ressaca
• Lembretes inteligentes para beber água entre os drinks
• Nível de risco de ressaca para o dia seguinte com dicas de recuperação
• Dicas de hidratação e eletrólitos antes de dormir

📍 Compartilhe sua noite ao vivo
• Envie um link com mapa em tempo real válido de 30 minutos até 4 horas
• Amigos acompanham seu local, bar onde você está e bateria do celular
• Sem complicações: funciona em qualquer navegador, sem precisar instalar app!

🚕 Transporte seguro e emergência
• Botão de 1 toque para pedir Uber ou transporte direto para onde você está
• Discagem rápida para seu contato de confiança ou serviços de emergência
• Cópia rápida do endereço para passar ao motorista

📅 Calendário e histórico mensal
• Calendário visual destacando dias sóbrios, moderados e de festa
• Acompanhe suas sequências de sobriedade e gastos mensais
• Dados 100% privados salvos no seu aparelho

⚠️ Aviso importante de segurança
O TipsyBuddy fornece estimativas teóricas para fins informativos e de redução de danos. O metabolismo varia conforme alimentação, descanso e biologia individual. Nunca beba e dirija. Chame sempre um motorista de aplicativo, táxi ou carona designada."""
    },
    "it-IT": {
        "title": "TipsyBuddy: Traccia Drink",
        "shortDescription": "Traccia drink, stima tasso alcolemico, condividi la posizione e torna al sicuro.",
        "fullDescription": """Fai festa responsabilmente. Resta al sicuro. Svegliati bene domani.

TipsyBuddy è il tuo compagno per le uscite serali. Tieni traccia dei tuoi drink, controlla la stima del tasso alcolemico e il conto alla rovescia della sobrietà, condividi la tua posizione in tempo reale con gli amici e torna a casa in totale sicurezza.

📊 Tieni d'occhio il tuo stato
• Calcolo stimato del tasso alcolemico in tempo reale basato sul tuo profilo
• Timer di recupero che indica approssimativamente quando tornerai sobrio
• Livelli colorati per aiutarti a bere con intelligenza

🍺 Registra drink con 1 tocco
• Tasti rapidi per Birra, Vino, Cocktail, Shot e Acqua
• Conteggio dei drink e conto totale della spesa della serata
• Aggiungi bevande personalizzate con gradazione, volume e costo

💧 Rimani idratato ed evita il doposbornia
• Promemoria intelligenti per bere acqua tra un drink e l'altro
• Valutazione del rischio di postumi per il giorno dopo con consigli utili
• Consigli per l'idratazione prima di andare a letto

📍 Condividi la tua posizione dal vivo
• Invia agli amici una mappa in tempo reale da 30 minuti a 4 ore
• Vedono la tua posizione, il locale e la batteria dello smartphone
• Senza attrito: si apre in qualsiasi browser senza installare nulla!

🚕 Ritorno sicuro & Emergenze
• Chiama Uber o un taxi con 1 tocco verso la tua posizione esatta
• Chiamata rapida per il tuo contatto fidato o i servizi di emergenza

📅 Calendario storico e spese
• Calendario mensile con giorni sobri, moderati o di festa
• Traccia serie di sobrietà e budget mensile delle serate
• Dati salvati privatamente solo sul tuo telefono

⚠️ Avviso di sicurezza importante
Le stime sono calcolate a solo scopo informativo e di prevenzione. Il metabolismo varia in base al cibo, idratazione e organismo. Non guidare mai dopo aver bevuto. Affidati sempre a un guidatore designato, taxi o trasporti pubblici."""
    },
    "ja-JP": {
        "title": "TipsyBuddy: 飲酒・アルコール記録",
        "shortDescription": "飲酒と血中アルコール濃度を記録。友達への現在地共有や配車呼出で安全な夜を。",
        "fullDescription": """スマートに飲んで、安全に帰宅。翌朝もすっきり。

TipsyBuddyは、あなたのナイトライフと安全をサポートするドリンク管理アプリです。飲んだお酒をワンタップで記録し、血中アルコール濃度（BAC）やシラフに戻るまでの予測時間をリアルタイムで確認できます。友達への現在地共有やタクシー・配車サービス呼び出し機能も搭載。

📊 アルコール状態をひと目で把握
• あなたの体格情報に基づいた血中アルコール濃度（BAC）推計
• 完全にシラフに戻るまでのカウントダウンタイマー
• 色分けされた安全レベル表示で飲みすぎを防止

🍺 ワンタップで簡単ドリンク記録
• ビール、ワイン、カクテル、ショット、お水をワンタップで登録
• 飲んだ杯数とお会計の合計金額をリアルタイム追跡
• 度数・容量・価格を自由に設定できるカスタムドリンク登録

💧 こまめな水分補給で二日酔いを予防
• お酒の合間にお水を飲むスマートリマインダー
• 翌日の二日酔いリスク診断と回復アドバイス
• 就寝前の水分補給と電解質ケアのヒント

📍 友達にリアルタイム現在地を共有
• 30分〜4時間の期間限定で現在地マップリンクを発行
• 友達はアプリ不要！ブラウザを開くだけで場所・お店・バッテリー残量を確認可能
• 待ち合わせや夜道の一人歩き、帰宅時の見守りに最適

🚕 ワンタップ配車＆緊急連絡
• Uberなどの配車アプリを現在地へワンタップで呼び出し
• 登録した緊急連絡先や緊急通報へすぐに発信可能
• 運転代行やタクシーに伝えやすい現在地住所のコピー機能

📅 飲酒カレンダー＆習慣チェック
• 休肝日・適量・飲みすぎた日をカレンダーで可視化
• 連続休肝日記録や月間の夜遊び出費をチェック
• データは端末内に安全に保存され、プライバシーも万全

⚠️ 安全に関する重要なお知らせ
本アプリの計算結果は一般的な理論計算に基づく推計値であり、医学的な診断や飲酒運転の可否を判断するものではありません。アルコールの代謝速度は体調や食事によって大きく異なります。飲酒運転は法律で固く禁止されています。必ずタクシー、運転代行、または公共交通機関をご利用ください。"""
    }
}


def main():
    print("=== Validating Google Play Store Listings ===")
    errors = []

    for locale, data in LISTINGS.items():
        title = data["title"]
        short_desc = data["shortDescription"]
        full_desc = data["fullDescription"]

        t_len = len(title)
        s_len = len(short_desc)
        f_len = len(full_desc)

        print(f"[{locale}] Title: {t_len}/30 | Short: {s_len}/80 | Full: {f_len}/4000")

        if t_len > 30:
            errors.append(f"[{locale}] Title length {t_len} exceeds 30: '{title}'")
        if s_len > 80:
            errors.append(f"[{locale}] Short description length {s_len} exceeds 80: '{short_desc}'")
        if f_len > 4000:
            errors.append(f"[{locale}] Full description length {f_len} exceeds 4000")

    if errors:
        print("\nERRORS FOUND:")
        for e in errors:
            print(" -", e)
        raise ValueError("One or more listings exceed Google Play character limits")

    out_file = os.path.join(ASSETS_DIR, "listings.json")
    with open(out_file, "w", encoding="utf-8") as f:
        json.dump(LISTINGS, f, indent=2, ensure_ascii=False)

    print(f"\nALL LISTINGS VALIDATED SUCCESSFULLY! Saved to: {out_file}")


if __name__ == "__main__":
    main()
