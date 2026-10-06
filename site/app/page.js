"use client";

import Image from "next/image";
import {
  ArrowDownToLine,
  Github,
  Keyboard,
  Monitor,
  MonitorDown,
  MousePointer2,
  Music2,
  Play,
  ShieldCheck,
  Smartphone,
  Wifi,
} from "lucide-react";
import { useState } from "react";

const RUSTORE_URL = "https://www.rustore.ru/catalog/app/ru.ladon.remote";
const GITHUB_URL = "https://github.com/MkinG2k0/ClickMate";

const demos = [
  {
    id: "mouse",
    label: "Мышь",
    title: "Тачпад, который уже в руке",
    text: "Ведите одним пальцем, прокручивайте двумя. Касание работает как клик, а двойное касание с удержанием — как перетаскивание.",
    image: "/screens/mouse.webp",
    icon: MousePointer2,
  },
  {
    id: "movie",
    label: "Фильм",
    title: "Кино — без похода к компьютеру",
    text: "Пауза, громкость, перемотка и компактный тачпад собраны на одном экране для дивана и большого монитора.",
    image: "/screens/movie.webp",
    icon: Play,
  },
  {
    id: "keyboard",
    label: "Клавиатура",
    title: "Печатайте на телефоне",
    text: "Отправляйте текст на русском и английском, используйте Enter, стрелки, копирование, вставку и другие привычные сочетания.",
    image: "/screens/keyboard.webp",
    icon: Keyboard,
  },
  {
    id: "media",
    label: "Медиа",
    title: "Музыка под контролем",
    text: "Переключайте треки, ставьте воспроизведение на паузу и меняйте громкость стандартными медиаклавишами Windows.",
    image: "/screens/media.webp",
    icon: Music2,
  },
];

function Brand() {
  return (
    <a className="brand" href="#top" aria-label="ClickMate — на главную">
      <span className="brand-mark"><MousePointer2 size={18} strokeWidth={2.4} /></span>
      <span>ClickMate</span>
    </a>
  );
}

export default function Home() {
  const [active, setActive] = useState(demos[0]);

  return (
    <main id="top">
      <header className="nav shell">
        <Brand />
        <nav aria-label="Основная навигация">
          <a href="#demo">Возможности</a>
          <a href="#connect">Подключение</a>
          <a href="#download">Скачать</a>
        </nav>
        <a className="nav-github" href={GITHUB_URL} target="_blank" rel="noreferrer">
          <Github size={18} /> GitHub
        </a>
      </header>

      <section className="hero shell">
        <div className="hero-copy">
          <div className="availability"><span /> Для Windows 10/11 и Android 8+</div>
          <h1>Телефон — ваш пульт для Windows</h1>
          <p className="hero-lead">
            Управляйте курсором, печатайте и переключайте музыку с дивана. Без аккаунта, облака и лишних проводов.
          </p>
          <div className="hero-actions">
            <a className="button primary" href={RUSTORE_URL} target="_blank" rel="noreferrer">
              <Smartphone size={20} /> Скачать в RuStore
            </a>
            <a className="button secondary" href="/ClickMate-PC.exe" download>
              <Monitor size={20} /> Скачать для ПК
            </a>
          </div>
          <p className="download-note">Версия 0.12.0 · Бесплатно · Локальная сеть</p>
        </div>

        <div className="hero-visual" aria-label="ClickMate подключает Android-телефон к компьютеру Windows">
          <div className="signal signal-one" />
          <div className="signal signal-two" />
          <div className="pc-card">
            <div className="pc-top"><span /><span /><span /></div>
            <div className="pc-screen">
              <div className="windows-glyph"><i /><i /><i /><i /></div>
              <strong>ClickMate для Windows</strong>
              <div className="connected"><span /> Доступ включён</div>
            </div>
            <div className="pc-foot" />
          </div>
          <div className="phone-frame">
            <div className="phone-speaker" />
            <div className="phone-ui">
              <div className="phone-head"><b>ClickMate</b><span>● Подключено</span></div>
              <div className="phone-tabs"><b>Мышь</b><span>Фильм</span><span>Медиа</span></div>
              <div className="mini-pad"><MousePointer2 size={34} /><small>Тачпад</small></div>
              <div className="mini-buttons"><span>Левый щелчок</span><span>Правый щелчок</span></div>
            </div>
          </div>
          <div className="connection-badge"><Wifi size={18} /><span><b>Одна Wi‑Fi сеть</b><small>Данные остаются дома</small></span></div>
        </div>
      </section>

      <section className="trust-strip" aria-label="Преимущества">
        <div className="shell">
          <span><ShieldCheck /> Без регистрации</span>
          <span><Wifi /> Работает локально</span>
          <span><MonitorDown /> Лёгкая программа для ПК</span>
        </div>
      </section>

      <section className="demo-section shell" id="demo">
        <div className="section-intro">
          <h2>Всё нужное — под большим пальцем</h2>
          <p>Выберите режим и посмотрите, как ClickMate выглядит на телефоне.</p>
        </div>
        <div className="demo-stage">
          <div className="demo-copy">
            <div className="demo-tabs" role="tablist" aria-label="Режимы ClickMate">
              {demos.map((item) => {
                const Icon = item.icon;
                return (
                  <button
                    key={item.id}
                    className={active.id === item.id ? "active" : ""}
                    onClick={() => setActive(item)}
                    role="tab"
                    aria-selected={active.id === item.id}
                  >
                    <Icon size={19} /> {item.label}
                  </button>
                );
              })}
            </div>
            <div className="demo-text" key={active.id}>
              <h3>{active.title}</h3>
              <p>{active.text}</p>
            </div>
            <div className="gesture-list">
              <div><span>1</span><p><b>Коснитесь</b><small>для обычного щелчка</small></p></div>
              <div><span>2</span><p><b>Проведите</b><small>чтобы двигать курсор</small></p></div>
              <div><span>↕</span><p><b>Два пальца</b><small>для прокрутки</small></p></div>
            </div>
          </div>
          <div className="screenshot-wrap">
            <Image
              key={active.image}
              src={active.image}
              alt={`Экран режима «${active.label}» в ClickMate`}
              width={942}
              height={1674}
              className="app-screenshot"
              priority={active.id === "mouse"}
            />
          </div>
        </div>
      </section>

      <section className="connect-section" id="connect">
        <div className="shell connect-grid">
          <div className="connect-title">
            <h2>Подключение занимает минуту</h2>
            <p>Оба устройства должны быть в одной домашней сети.</p>
          </div>
          <ol className="steps">
            <li><span>1</span><div><b>Запустите программу на ПК</b><p>ClickMate покажет QR‑код и четыре цифры.</p></div></li>
            <li><span>2</span><div><b>Откройте приложение на телефоне</b><p>Нажмите «Сканировать QR» или введите код.</p></div></li>
            <li><span>3</span><div><b>Управляйте</b><p>После первого раза устройство подключится автоматически.</p></div></li>
          </ol>
        </div>
      </section>

      <section className="feature-section shell">
        <article className="feature-main">
          <div>
            <span className="feature-icon"><MousePointer2 /></span>
            <h2>Соберите свой пульт</h2>
            <p>Добавляйте кнопки команд, выбирайте цвет и ширину, меняйте их местами. До 12 персональных разделов.</p>
          </div>
          <div className="custom-panel">
            <span className="wide">Презентация</span><span>Назад</span><span>Вперёд</span><span>Громкость −</span><span>Громкость +</span><span className="wide accent">Пуск / пауза</span>
          </div>
        </article>
        <article className="feature-small secure">
          <ShieldCheck />
          <h3>Без облака</h3>
          <p>Команды идут напрямую между телефоном и компьютером внутри вашей сети.</p>
        </article>
        <article className="feature-small quiet">
          <MonitorDown />
          <h3>Всегда рядом</h3>
          <p>Программа может запускаться вместе с Windows и работать из системного трея.</p>
        </article>
      </section>

      <section className="download-section" id="download">
        <div className="shell download-card">
          <div className="download-copy">
            <Image src="/icon.png" alt="" width={72} height={72} />
            <div><h2>Попробуйте ClickMate сегодня</h2><p>Установите две части приложения и соедините их в одной Wi‑Fi сети.</p></div>
          </div>
          <div className="download-actions">
            <a className="store-button" href={RUSTORE_URL} target="_blank" rel="noreferrer">
              <Smartphone /><span><small>ДЛЯ ANDROID</small><b>Скачать в RuStore</b></span>
            </a>
            <a className="store-button light" href="/ClickMate-PC.exe" download>
              <ArrowDownToLine /><span><small>ДЛЯ WINDOWS 10/11</small><b>Скачать для ПК</b></span>
            </a>
          </div>
        </div>
      </section>

      <footer className="footer shell">
        <Brand />
        <p>Локальный пульт для Windows с телефона.</p>
        <div><a href={GITHUB_URL} target="_blank" rel="noreferrer"><Github size={18} /> GitHub</a><a href="#top">Наверх</a></div>
      </footer>
    </main>
  );
}
