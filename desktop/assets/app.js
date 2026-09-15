/* WebView2 desktop UI for the Kos Manyar water-bill receipt printer. */
(function () {
  "use strict";

  var bridge = window.chrome && window.chrome.webview;

  var state = {
    settings: { period: "", month: "", year: new Date().getFullYear(), printerName: "POS-58", garbageFee: 8498 },
    months: [],
    units: [],
    bills: [],
    totals: { units: 0, usageM3: 0, grandFormatted: "Rp. 0", waterFormatted: "Rp. 0", garbageFormatted: "Rp. 0" },
    dataFile: "",
    selectedId: null,
  };

  var el = {};

  function $(id) { return document.getElementById(id); }

  function cacheElements() {
    [
      "monthSelect", "yearInput", "garbageInput", "applyPeriod", "prevMonth", "nextMonth",
      "unitRows", "unitCount", "totalM3", "totalWater", "totalGarbage", "totalGrand",
      "printAll", "resetUnits", "openData",
      "unitForm", "unitId", "unitName", "unitUsage", "unitTotal", "computedWater", "computedGarbage",
      "formTitle", "clearForm", "deleteUnit",
      "printerName", "checkPrinter", "printerStatus", "printDelay",
      "previewSelect", "previewBtn", "printBtn", "preview", "printFrame", "toast",
    ].forEach(function (id) { el[id] = $(id); });
  }

  function post(type, payload) {
    if (bridge) bridge.postMessage({ type: type, payload: payload || {} });
  }

  function formatRupiah(value) {
    return "Rp. " + Number(value || 0).toLocaleString("id-ID");
  }

  function escapeHtml(value) {
    return String(value == null ? "" : value)
      .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
  }

  var toastTimer = null;
  function toast(message, isError) {
    if (!message) return;
    el.toast.textContent = message;
    el.toast.className = "toast " + (isError ? "error" : "ok");
    el.toast.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { el.toast.hidden = true; }, 4200);
  }

  /* ---------------- Rendering ---------------- */

  function renderState(next) {
    Object.assign(state, next);

    el.monthSelect.innerHTML = state.months.map(function (month) {
      var selected = month === state.settings.month ? " selected" : "";
      return '<option value="' + escapeHtml(month) + '"' + selected + ">" + escapeHtml(month) + "</option>";
    }).join("");

    if (document.activeElement !== el.yearInput) el.yearInput.value = state.settings.year;
    if (document.activeElement !== el.garbageInput) el.garbageInput.value = state.settings.garbageFee;
    if (document.activeElement !== el.printerName) el.printerName.value = state.settings.printerName;

    renderUnits();
    renderPreviewOptions();
    renderTotals();
  }

  function renderUnits() {
    if (!state.bills.length) {
      el.unitRows.innerHTML = '<tr><td colspan="7" style="text-align:center;color:#64748b;padding:18px">' +
        "Belum ada unit. Tambahkan unit baru pada panel kanan.</td></tr>";
    } else {
      el.unitRows.innerHTML = state.bills.map(function (bill, index) {
        var selected = bill.id === state.selectedId ? " class=\"selected\"" : "";
        return "<tr data-id=\"" + escapeHtml(bill.id) + "\"" + selected + ">" +
          "<td>" + (index + 1) + "</td>" +
          '<td class="name">' + escapeHtml(bill.name) + "</td>" +
          '<td class="num">' + escapeHtml(bill.usageM3) + "</td>" +
          '<td class="num">' + escapeHtml(bill.waterBillFormatted) + "</td>" +
          '<td class="num">' + escapeHtml(bill.garbageFeeFormatted) + "</td>" +
          '<td class="num"><b>' + escapeHtml(bill.totalFormatted) + "</b></td>" +
          '<td><div class="row-actions">' +
          '<button type="button" class="ghost" data-action="edit">Ubah</button>' +
          '<button type="button" class="primary" data-action="print">Cetak</button>' +
          "</div></td></tr>";
      }).join("");
    }

    el.unitCount.textContent = state.bills.length + " unit";
  }

  function renderTotals() {
    var totals = state.totals || {};
    el.totalM3.textContent = totals.usageM3 || 0;
    el.totalWater.textContent = totals.waterFormatted || formatRupiah(0);
    el.totalGarbage.textContent = totals.garbageFormatted || formatRupiah(0);
    el.totalGrand.textContent = totals.grandFormatted || formatRupiah(0);
  }

  function renderPreviewOptions() {
    var current = el.previewSelect.value;
    el.previewSelect.innerHTML = state.bills.map(function (bill) {
      return '<option value="' + escapeHtml(bill.id) + '">' + escapeHtml(bill.name) + "</option>";
    }).join("");

    if (state.selectedId && state.bills.some(function (b) { return b.id === state.selectedId; })) {
      el.previewSelect.value = state.selectedId;
    } else if (current && state.bills.some(function (b) { return b.id === current; })) {
      el.previewSelect.value = current;
    }
  }

  function renderComputed() {
    var fee = Number(el.garbageInput.value) || 0;
    var total = Number(el.unitTotal.value) || 0;
    el.computedGarbage.textContent = formatRupiah(fee);
    el.computedWater.textContent = formatRupiah(total - fee);
  }

  function showPreview(name, text) {
    el.preview.textContent = text;
    if (name) toast("Pratinjau nota " + name);
  }

  /* ---------------- Selection / form ---------------- */

  function findBill(id) {
    return state.bills.filter(function (b) { return b.id === id; })[0];
  }

  function selectUnit(id) {
    state.selectedId = id;
    var bill = findBill(id);

    Array.prototype.forEach.call(el.unitRows.querySelectorAll("tr"), function (row) {
      row.classList.toggle("selected", row.getAttribute("data-id") === id);
    });

    if (!bill) return;

    el.previewSelect.value = id;
    el.unitId.value = bill.id;
    el.unitName.value = bill.name;
    el.unitUsage.value = bill.usageM3;
    el.unitTotal.value = bill.total;
    el.formTitle.textContent = "Ubah Unit: " + bill.name;
    el.deleteUnit.hidden = false;
    renderComputed();
    requestPreview();
  }

  function clearForm() {
    state.selectedId = null;
    el.unitId.value = "";
    el.unitForm.reset();
    el.unitUsage.value = 0;
    el.unitTotal.value = 0;
    el.formTitle.textContent = "Tambah / Ubah Unit";
    el.deleteUnit.hidden = true;
    Array.prototype.forEach.call(el.unitRows.querySelectorAll("tr"), function (row) {
      row.classList.remove("selected");
    });
    renderComputed();
  }

  function requestPreview() {
    var id = state.selectedId || el.previewSelect.value;
    if (!id) { toast("Pilih unit terlebih dahulu.", true); return; }
    post("preview", { id: id });
  }

  function shiftMonth(delta) {
    var monthIndex = state.months.indexOf(el.monthSelect.value);
    if (monthIndex < 0) monthIndex = state.months.indexOf(state.settings.month);
    var year = Number(el.yearInput.value) || state.settings.year;
    var next = monthIndex + delta;
    if (next < 0) { next = 11; year -= 1; }
    if (next > 11) { next = 0; year += 1; }
    el.monthSelect.value = state.months[next];
    el.yearInput.value = year;
    applyPeriod();
  }

  function applyPeriod() {
    post("saveSettings", {
      month: el.monthSelect.value,
      year: Number(el.yearInput.value) || state.settings.year,
      garbageFee: Number(el.garbageInput.value) || 0,
      printerName: el.printerName.value.trim() || state.settings.printerName,
    });
  }

  /* ---------------- Events ---------------- */

  function bindEvents() {
    el.unitRows.addEventListener("click", function (event) {
      var button = event.target.closest("button[data-action]");
      var row = event.target.closest("tr[data-id]");
      if (!row) return;
      var id = row.getAttribute("data-id");
      var bill = findBill(id);
      if (!bill) return;

      if (!button) { selectUnit(id); return; }

      if (button.getAttribute("data-action") === "edit") {
        selectUnit(id);
      } else if (button.getAttribute("data-action") === "print") {
        state.selectedId = id;
        post("print", { id: id, printerName: el.printerName.value.trim() });
      }
    });

    el.unitForm.addEventListener("submit", function (event) {
      event.preventDefault();
      post("saveUnit", {
        id: el.unitId.value || null,
        name: el.unitName.value.trim(),
        usageM3: Number(el.unitUsage.value) || 0,
        total: Number(el.unitTotal.value) || 0,
      });
    });

    el.deleteUnit.addEventListener("click", function () {
      var id = el.unitId.value;
      if (!id) return;
      if (!window.confirm("Hapus unit '" + el.unitName.value + "' dari daftar?")) return;
      post("deleteUnit", { id: id, name: el.unitName.value });
      clearForm();
    });

    el.unitUsage.addEventListener("input", renderComputed);
    el.unitTotal.addEventListener("input", renderComputed);
    el.garbageInput.addEventListener("input", renderComputed);

    el.applyPeriod.addEventListener("click", applyPeriod);
    el.prevMonth.addEventListener("click", function () { shiftMonth(-1); });
    el.nextMonth.addEventListener("click", function () { shiftMonth(1); });

    el.previewSelect.addEventListener("change", function () {
      state.selectedId = el.previewSelect.value;
      var bill = findBill(state.selectedId);
      if (bill) {
        el.unitId.value = bill.id;
        el.unitName.value = bill.name;
        el.unitUsage.value = bill.usageM3;
        el.unitTotal.value = bill.total;
        el.formTitle.textContent = "Ubah Unit: " + bill.name;
        el.deleteUnit.hidden = false;
        renderComputed();
      }
      Array.prototype.forEach.call(el.unitRows.querySelectorAll("tr"), function (row) {
        row.classList.toggle("selected", row.getAttribute("data-id") === state.selectedId);
      });
      requestPreview();
    });

    el.previewBtn.addEventListener("click", requestPreview);

    el.printBtn.addEventListener("click", function () {
      var id = state.selectedId || el.previewSelect.value;
      if (!id) { toast("Pilih unit terlebih dahulu.", true); return; }
      post("print", { id: id, printerName: el.printerName.value.trim() });
    });

    el.printAll.addEventListener("click", function () {
      if (!state.bills.length) { toast("Belum ada unit untuk dicetak.", true); return; }
      if (!window.confirm("Cetak nota untuk " + state.bills.length + " unit?")) return;
      el.printAll.disabled = true;
      post("printAll", {
        printerName: el.printerName.value.trim(),
        delayMs: Number(el.printDelay.value) || 1500,
      });
    });

    el.checkPrinter.addEventListener("click", function () {
      el.printerStatus.className = "status";
      el.printerStatus.textContent = "Memeriksa...";
      post("checkPrinter", { printerName: el.printerName.value.trim() });
    });

    el.resetUnits.addEventListener("click", function () {
      if (!window.confirm("Kembalikan daftar unit ke data bawaan?")) return;
      post("resetUnits", {});
    });

    el.openData.addEventListener("click", function () { post("openDataFolder", {}); });
    el.clearForm.addEventListener("click", clearForm);
  }

  /* ---------------- Bridge ---------------- */

  function handleMessage(message) {
    switch (message.type) {
      case "state":
        renderState(message.payload);
        if (message.message) toast(message.message);
        break;
      case "preview":
        if (message.extra) showPreview(message.extra.name, message.extra.text);
        break;
      case "printResult":
        toast(message.message || "Selesai.", message.isError);
        if (message.extra && message.extra.preview) showPreview(message.extra.name, message.extra.preview);
        break;
      case "printProgress":
        if (message.extra) {
          el.printerStatus.className = "status";
          el.printerStatus.textContent = "Mencetak " + message.extra.index + "/" + message.extra.total + ": " + message.extra.name;
        }
        break;
      case "printAllDone":
        el.printAll.disabled = false;
        toast(message.message, message.isError);
        if (message.extra) {
          el.printerStatus.className = "status " + (message.extra.failed ? "err" : "ok");
          el.printerStatus.textContent = message.extra.total - message.extra.failed + " berhasil, " + message.extra.failed + " gagal";
        }
        break;
      case "printerStatus":
        if (message.extra) {
          el.printerStatus.className = "status " + (message.extra.available ? "ok" : "err");
          el.printerStatus.textContent = message.extra.available
            ? "Siap - port " + message.extra.port + " (" + message.extra.driver + ")"
            : message.extra.status;
        }
        break;
      case "toast":
        toast(message.message, message.isError);
        break;
    }
  }

  function start() {
    cacheElements();
    bindEvents();
    renderComputed();

    if (bridge) {
      bridge.addEventListener("message", function (event) { handleMessage(event.data || {}); });
      post("ready", {});
    } else {
      toast("Bridge WebView2 tidak tersedia.", true);
    }
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", start);
  } else {
    start();
  }
})();
