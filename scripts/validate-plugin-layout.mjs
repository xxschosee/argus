#!/usr/bin/env node
// Проверка вёрстки плагина Argus: дескрипторы, главный класс, анти-паттерны.
// Использование: node scripts/validate-plugin-layout.mjs [--strict]
import { readFileSync, existsSync, readdirSync, statSync } from "node:fs";
import { join, dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const ROOT = resolve(dirname(fileURLToPath(import.meta.url)), "..");
const STRICT = process.argv.includes("--strict");

let errors = 0;
let warnings = 0;
const err = (m) => { console.log(`ERROR: ${m}`); errors++; };
const warn = (m) => { console.log(`WARN:  ${m}`); warnings++; };
const ok = (m) => console.log(`ok:    ${m}`);

/** Минимальный разбор YAML: верхний уровень + вложенные секции по 2 пробелам. */
function parseYaml(text) {
  const root = {};
  let section = null;
  for (const rawLine of text.split(/\r?\n/)) {
    if (!rawLine.trim() || rawLine.trim().startsWith("#")) continue;
    const indent = rawLine.length - rawLine.trimStart().length;
    const line = rawLine.trim();
    if (indent === 0) {
      const idx = line.indexOf(":");
      if (idx === -1) continue;
      const key = line.slice(0, idx).trim();
      const value = line.slice(idx + 1).trim();
      if (value === "") {
        section = {};
        root[key] = section;
      } else {
        section = null;
        root[key] = value.replace(/^['"]|['"]$/g, "");
      }
    } else if (section && indent >= 2) {
      const idx = line.indexOf(":");
      if (idx === -1) continue;
      section[line.slice(0, idx).trim()] = line.slice(idx + 1).trim().replace(/^['"]|['"]$/g, "");
    }
  }
  return root;
}

function walk(dir, out = []) {
  if (!existsSync(dir)) return out;
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) walk(full, out);
    else if (full.endsWith(".java")) out.push(full);
  }
  return out;
}

// ---------------------------------------------------------------- дескриптор
const descriptorPath = join(ROOT, "src", "main", "resources", "plugin.yml");
if (!existsSync(descriptorPath)) {
  err("нет src/main/resources/plugin.yml");
} else {
  ok("найден src/main/resources/plugin.yml");
  const yaml = parseYaml(readFileSync(descriptorPath, "utf8"));

  for (const key of ["name", "version", "main", "api-version"]) {
    if (yaml[key] !== undefined && yaml[key] !== "") ok(`plugin.yml: ключ '${key}' есть`);
    else err(`plugin.yml: отсутствует обязательный ключ '${key}'`);
  }

  const api = String(yaml["api-version"] ?? "");
  if (/^(26\.\d+(\.\d+)?|1\.21(\.\d+)?)$/.test(api)) ok(`api-version '${api}' поддерживается`);
  else if (api === "") err("не удалось прочитать api-version");
  else err(`api-version '${api}' не поддерживается (нужен 26.x или 1.21.x)`);

  const mainClass = String(yaml.main ?? "");
  if (mainClass) {
    const mainPath = join(ROOT, "src", "main", "java", ...mainClass.split(".")) + ".java";
    if (existsSync(mainPath)) {
      ok(`главный класс найден: ${mainClass}`);
      if (readFileSync(mainPath, "utf8").includes("extends JavaPlugin")) {
        ok("главный класс наследуется от JavaPlugin");
      } else {
        err("главный класс не наследуется от JavaPlugin");
      }
    } else {
      err(`главный класс не найден: ${mainPath}`);
    }
  }

  if (yaml.commands) ok("plugin.yml: секция commands присутствует");
  else warn("plugin.yml: нет секции commands");

  if (String(yaml["folia-supported"] ?? "") === "true") {
    warn("заявлена поддержка Folia - убедись, что она реально проверена");
  } else {
    ok("поддержка Folia не заявлена (корректно для текущей версии)");
  }
}

if (!existsSync(join(ROOT, "src", "main", "resources", "config.yml"))) {
  err("нет src/main/resources/config.yml");
} else {
  ok("найден src/main/resources/config.yml");
}

// ---------------------------------------------------------------- анти-паттерны
const sources = walk(join(ROOT, "src", "main", "java"));
if (sources.some((f) => readFileSync(f, "utf8").includes("Bukkit.reload("))) {
  err("найден Bukkit.reload() - перезагрузка сервера ломает плагины");
} else {
  ok("Bukkit.reload() не используется");
}

const coreSources = walk(join(ROOT, "src", "main", "java", "dev", "argus", "core"));
if (coreSources.some((f) => readFileSync(f, "utf8").includes("org.bukkit"))) {
  err("core импортирует org.bukkit - ядро должно быть проверяемо без сервера");
} else {
  ok("core не зависит от Bukkit");
}

if (sources.some((f) => readFileSync(f, "utf8").includes("getCommand(\"")) && !existsSync(descriptorPath)) {
  err("плагин регистрирует команды, но нет plugin.yml");
} else {
  ok("регистрация команд согласована с plugin.yml");
}

// ---------------------------------------------------------------- сборка
const libsDir = join(ROOT, "build", "libs");
let jar = null;
if (existsSync(libsDir)) {
  const jarName = readdirSync(libsDir).find((n) => n.endsWith(".jar") && !n.includes("sources"));
  if (jarName) {
    jar = join(libsDir, jarName);
    ok(`собран артефакт: ${jarName} (${statSync(jar).size} байт)`);
  }
}
if (!jar) warn("JAR не собран - выполни ./gradlew build");

// ---------------------------------------------------------------- итог
console.log("---------------------------------------------");
console.log(`ошибок: ${errors}, предупреждений: ${warnings}`);
if (errors > 0) {
  console.log("валидация не пройдена");
  process.exit(1);
}
if (STRICT && warnings > 0) {
  console.log("strict-режим: предупреждения считаются ошибками");
  process.exit(1);
}
console.log("валидация пройдена");
