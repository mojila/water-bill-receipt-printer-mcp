/* WebView2 desktop UI for the Kos Manyar water-bill receipt printer. */
(function () {
  "use strict";

  var webview = window.chrome && window.chrome.webview;

  var state = {
    settings: { period: "", month: "", year: new Date().getFullYear(), printerName: "POS-58", garbageFee: 8498 },
    months: [],
    units: [],
    bills: [],
    totals: { units: 0, usageM3: 0, grandFormatted: "Rp. 0", waterFormatted: "Rp. 0", garbageFormatted: "Rp. 0" },
    dataFile: "",
    selectedId: null,
    searchQuery: "",
  };

  var el = {};

  function $(id) { return document.getElementById(id); }

  function cacheElements() {
    [
      "monthSelect", "yearInput", "garbageInput", "applyPeriod", "prevMonth", "nextMonth",
      "topPrinterName", "printerStatusDot", "quickCheckPrinter",
      "unitRows", "unitCount", "unitSearch", "totalM3", "totalWater", "totalGarbage", "totalGrand",
      "printAll", "resetUnits", "openData",
      "unitForm", "unitId", "unitName", "unitUsage", "unitTotal", "computedWater", "computedGarbage",
      "formTitle", "clearForm", "deleteUnit",
      "printerName", "checkPrinter", "printerStatus", "printerBoxDot", "printDelay",
      "previewSelect", "previewBtn", "printBtn", "preview",
      "confirmModal", "modalTitle", "modalMessage", "modalCancelBtn", "modalConfirmBtn",
      "toast", "toastIcon", "toastMessage",
    ].forEach(function (id) { el[id] = $(id); });
  }

  /* ---------------- C# Bridge Integration ---------------- */

  function post(type, payload) {
    if (webview && typeof webview.postMessage === "function") {
      webview.postMessage({ type: type, payload: payload || {} });
    }
  }

  /* ---------------- Number & Formatting Helpers ---------------- */

  function formatRupiah(value) {
    return "Rp. " + Number(value || 0).toLocaleString("id-ID");
  }

  function formatNumberSeparator(num) {
    var parts = num.toString().split(".");
    parts[0] = parts[0].replace(/\B(?=(\d{3})+(?!\d))/g, ".");
    return parts.join(",");
  }

  function parseFormattedNumber(val) {
    if (typeof val === "number") return val;
    if (!val) return 0;
    var cleaned = String(val).replace(/[^0-9]/g, "");
    return Number(cleaned) || 0;
  }

  function escapeHtml(value) {
    return String(value == null ? "" : value)
      .replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
  }

  /* ---------------- Toast Notifications ---------------- */

  var toastTimer = null;
  function toast(message, isError) {
    if (!message) return;
    if (el.toastMessage) el.toastMessage.textContent = message;
    else el.toast.textContent = message;

    if (el.toastIcon) {
      el.toastIcon.innerHTML = isError
        ? '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><circle cx="12" cy="12" r="10"></circle><line x1="12" y1="8" x2="12" y2="12"></line><line x1="12" y1="16" x2="12.01" y2="16"></line></svg>'
        : '<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"><polyline points="20 6 9 17 4 12"></polyline></svg>';
    }

    el.toast.className = "toast " + (isError ? "error" : "ok");
    el.toast.hidden = false;
    el.toast.style.display = "flex";
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () {
      el.toast.hidden = true;
      el.toast.style.display = "none";
    }, 4200);
  }

  /* ---------------- Custom Confirmation Modal ---------------- */

  var pendingConfirmAction = null;

  function showConfirmModal(title, message, isDanger, onConfirm) {
    el.modalTitle.textContent = title || "Konfirmasi";
    el.modalMessage.textContent = message;
    el.modalConfirmBtn.className = "primary " + (isDanger ? "danger" : "");
    el.confirmModal.hidden = false;
    el.confirmModal.style.display = "flex";
    pendingConfirmAction = onConfirm;
    el.modalConfirmBtn.focus();
  }

  function closeConfirmModal() {
    el.confirmModal.hidden = true;
    el.confirmModal.style.display = "none";
    pendingConfirmAction = null;
    if (document.activeElement === el.modalConfirmBtn) el.modalConfirmBtn.blur();
  }

  /* ---------------- Rendering ---------------- */

  function renderState(next) {
    Object.assign(state, next);

    if (state.months && state.months.length) {
      el.monthSelect.innerHTML = state.months.map(function (month) {
        var selected = month === state.settings.month ? " selected" : "";
        return '<option value="' + escapeHtml(month) + '"' + selected + ">" + escapeHtml(month) + "</option>";
      }).join("");
    }

    if (document.activeElement !== el.yearInput) el.yearInput.value = state.settings.year;
    if (document.activeElement !== el.garbageInput) el.garbageInput.value = state.settings.garbageFee;
    if (document.activeElement !== el.printerName) el.printerName.value = state.settings.printerName;
    if (el.topPrinterName) el.topPrinterName.textContent = state.settings.printerName || "POS-58";

    renderUnits();
    renderPreviewOptions();
    renderTotals();
  }

  function getFilteredBills() {
    var query = (state.searchQuery || "").trim().toLowerCase();
    if (!query) return state.bills;
    return state.bills.filter(function (bill) {
      return (bill.name || "").toLowerCase().indexOf(query) !== -1;
    });
  }

  function renderUnits() {
    var filtered = getFilteredBills();

    if (!state.bills.length) {
      el.unitRows.innerHTML = '<tr><td colspan="7" style="text-align:center;color:#64748b;padding:24px 16px">' +
        "Belum ada unit. Tambahkan unit baru pada panel formulir.</td></tr>";
    } else if (!filtered.length) {
      el.unitRows.innerHTML = '<tr><td colspan="7" style="text-align:center;color:#64748b;padding:24px 16px">' +
        'Tidak ditemukan unit dengan kata kunci "' + escapeHtml(state.searchQuery) + '".</td></tr>';
    } else {
      el.unitRows.innerHTML = filtered.map(function (bill, index) {
        var selected = bill.id === state.selectedId ? ' class="selected"' : "";
        return '<tr data-id="' + escapeHtml(bill.id) + '"' + selected + ">" +
          '<td class="col-idx">' + (index + 1) + "</td>" +
          '<td class="name">' + escapeHtml(bill.name) + "</td>" +
          '<td class="num">' + escapeHtml(bill.usageM3) + "</td>" +
          '<td class="num">' + escapeHtml(bill.waterBillFormatted) + "</td>" +
          '<td class="num">' + escapeHtml(bill.garbageFeeFormatted) + "</td>" +
          '<td class="num col-total"><b>' + escapeHtml(bill.totalFormatted) + "</b></td>" +
          '<td><div class="row-actions">' +
          '<button type="button" class="ghost row-btn" data-action="edit" title="Ubah data unit">Ubah</button>' +
          '<button type="button" class="primary row-btn" data-action="print" title="Cetak nota ini">Cetak</button>' +
          "</div></td></tr>";
      }).join("");
    }

    el.unitCount.textContent = (state.searchQuery ? filtered.length + "/" : "") + state.bills.length + " unit";
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
    var total = parseFormattedNumber(el.unitTotal.value) || 0;
    el.computedGarbage.textContent = formatRupiah(fee);
    el.computedWater.textContent = formatRupiah(Math.max(0, total - fee));
  }

  function showPreview(name, text) {
    el.preview.textContent = text;
    if (name) toast("Pratinjau nota " + name);
  }

  /* ---------------- Selection & Form Logic ---------------- */

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
    el.unitTotal.value = formatNumberSeparator(bill.total);
    el.formTitle.textContent = "Ubah: " + bill.name;
    el.deleteUnit.hidden = false;
    renderComputed();
    requestPreview();
  }

  function clearForm() {
    state.selectedId = null;
    el.unitId.value = "";
    el.unitForm.reset();
    el.unitUsage.value = 0;
    el.unitTotal.value = "0";
    el.formTitle.textContent = "Tambah / Ubah Unit";
    el.deleteUnit.hidden = true;
    Array.prototype.forEach.call(el.unitRows.querySelectorAll("tr"), function (row) {
      row.classList.remove("selected");
    });
    renderComputed();
    el.unitName.focus();
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

  function checkPrinter() {
    var name = el.printerName.value.trim() || state.settings.printerName;
    el.printerStatus.className = "status";
    el.printerStatus.textContent = "Memeriksa...";
    if (el.printerBoxDot) el.printerBoxDot.className = "status-dot";
    if (el.printerStatusDot) el.printerStatusDot.className = "status-indicator-dot checking";
    post("checkPrinter", { printerName: name });
  }

  function setPrintButtonLoading(btn, isLoading) {
    if (!btn) return;
    var spinner = btn.querySelector(".btn-spinner");
    var icon = btn.querySelector(".btn-icon");
    btn.disabled = isLoading;
    if (spinner) spinner.hidden = !isLoading;
    if (icon) icon.hidden = isLoading;
  }

  /* ---------------- Event Listeners ---------------- */

  function bindEvents() {
    // Unit table click handling
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

    // Instant unit search filter
    if (el.unitSearch) {
      el.unitSearch.addEventListener("input", function () {
        state.searchQuery = el.unitSearch.value;
        renderUnits();
      });
    }

    // Number separator formatting for unit total input
    el.unitTotal.addEventListener("focus", function () {
      var numeric = parseFormattedNumber(el.unitTotal.value);
      if (numeric === 0) el.unitTotal.value = "";
      else el.unitTotal.value = String(numeric);
    });

    el.unitTotal.addEventListener("input", function () {
      renderComputed();
    });

    el.unitTotal.addEventListener("blur", function () {
      var numeric = parseFormattedNumber(el.unitTotal.value);
      el.unitTotal.value = formatNumberSeparator(numeric);
      renderComputed();
    });

    // Save unit form
    el.unitForm.addEventListener("submit", function (event) {
      event.preventDefault();
      var rawTotal = parseFormattedNumber(el.unitTotal.value);
      post("saveUnit", {
        id: el.unitId.value || null,
        name: el.unitName.value.trim(),
        usageM3: Number(el.unitUsage.value) || 0,
        total: rawTotal,
      });
    });

    // Delete unit with modern confirmation modal
    el.deleteUnit.addEventListener("click", function () {
      var id = el.unitId.value;
      var name = el.unitName.value.trim();
      if (!id) return;
      showConfirmModal(
        "Hapus Unit",
        "Apakah Anda yakin ingin menghapus unit '" + name + "' dari daftar tagihan?",
        true,
        function () {
          post("deleteUnit", { id: id, name: name });
          clearForm();
        }
      );
    });

    el.unitUsage.addEventListener("input", renderComputed);
    el.garbageInput.addEventListener("input", renderComputed);

    // Period controls
    el.applyPeriod.addEventListener("click", applyPeriod);
    el.prevMonth.addEventListener("click", function () { shiftMonth(-1); });
    el.nextMonth.addEventListener("click", function () { shiftMonth(1); });

    // Sync printer name input with top quick bar
    el.printerName.addEventListener("input", function () {
      if (el.topPrinterName) el.topPrinterName.textContent = el.printerName.value.trim() || "POS-58";
    });

    // Preview selection change
    el.previewSelect.addEventListener("change", function () {
      state.selectedId = el.previewSelect.value;
      var bill = findBill(state.selectedId);
      if (bill) {
        el.unitId.value = bill.id;
        el.unitName.value = bill.name;
        el.unitUsage.value = bill.usageM3;
        el.unitTotal.value = formatNumberSeparator(bill.total);
        el.formTitle.textContent = "Ubah: " + bill.name;
        el.deleteUnit.hidden = false;
        renderComputed();
      }
      Array.prototype.forEach.call(el.unitRows.querySelectorAll("tr"), function (row) {
        row.classList.toggle("selected", row.getAttribute("data-id") === state.selectedId);
      });
      requestPreview();
    });

    el.previewBtn.addEventListener("click", requestPreview);

    // Print single receipt
    el.printBtn.addEventListener("click", function () {
      var id = state.selectedId || el.previewSelect.value;
      if (!id) { toast("Pilih unit terlebih dahulu.", true); return; }
      setPrintButtonLoading(el.printBtn, true);
      post("print", { id: id, printerName: el.printerName.value.trim() });
    });

    // Print all receipts with modern confirmation modal
    el.printAll.addEventListener("click", function () {
      if (!state.bills.length) { toast("Belum ada unit untuk dicetak.", true); return; }
      showConfirmModal(
        "Cetak Semua Nota",
        "Mencetak " + state.bills.length + " nota tagihan sekaligus ke printer " + (el.printerName.value.trim() || "POS-58") + "?",
        false,
        function () {
          setPrintButtonLoading(el.printAll, true);
          post("printAll", {
            printerName: el.printerName.value.trim(),
            delayMs: Number(el.printDelay.value) || 1500,
          });
        }
      );
    });

    // Check printer buttons
    el.checkPrinter.addEventListener("click", checkPrinter);
    if (el.quickCheckPrinter) el.quickCheckPrinter.addEventListener("click", checkPrinter);

    // Reset default units
    el.resetUnits.addEventListener("click", function () {
      showConfirmModal(
        "Reset Data Bawaan",
        "Apakah Anda yakin ingin mengembalikan daftar unit ke data bawaan kos? Perubahan saat ini akan ditimpa.",
        true,
        function () {
          post("resetUnits", {});
        }
      );
    });

    el.openData.addEventListener("click", function () { post("openDataFolder", {}); });
    el.clearForm.addEventListener("click", clearForm);

    // Modal buttons
    el.modalCancelBtn.addEventListener("click", closeConfirmModal);
    el.modalConfirmBtn.addEventListener("click", function () {
      var action = pendingConfirmAction;
      closeConfirmModal();
      if (typeof action === "function") action();
    });

    // Close modal on backdrop click
    el.confirmModal.addEventListener("click", function (event) {
      if (event.target === el.confirmModal) closeConfirmModal();
    });

    // Global keyboard shortcuts
    window.addEventListener("keydown", function (event) {
      // Escape closes modal dialog
      if (event.key === "Escape" || event.keyCode === 27) {
        if (!el.confirmModal.hidden) {
          event.preventDefault();
          closeConfirmModal();
          return;
        }
      }

      // Enter confirms modal dialog if open
      if ((event.key === "Enter" || event.keyCode === 13) && !el.confirmModal.hidden) {
        event.preventDefault();
        var action = pendingConfirmAction;
        closeConfirmModal();
        if (typeof action === "function") action();
        return;
      }

      // Ctrl shortcuts
      if (event.ctrlKey || event.metaKey) {
        var key = event.key ? event.key.toLowerCase() : "";
        if (key === "s") {
          event.preventDefault();
          el.unitForm.requestSubmit();
        } else if (key === "p") {
          event.preventDefault();
          el.printBtn.click();
        } else if (key === "n") {
          event.preventDefault();
          clearForm();
        }
      }
    });
  }

  /* ---------------- Bridge Message Dispatcher ---------------- */

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
        setPrintButtonLoading(el.printBtn, false);
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
        setPrintButtonLoading(el.printAll, false);
        toast(message.message, message.isError);
        if (message.extra) {
          var okCount = message.extra.total - message.extra.failed;
          el.printerStatus.className = "status " + (message.extra.failed ? "err" : "ok");
          el.printerStatus.textContent = okCount + " berhasil, " + message.extra.failed + " gagal";
          if (el.printerBoxDot) el.printerBoxDot.className = "status-dot " + (message.extra.failed ? "err" : "ok");
          if (el.printerStatusDot) el.printerStatusDot.className = "status-indicator-dot " + (message.extra.failed ? "err" : "ok");
        }
        break;
      case "printerStatus":
        if (message.extra) {
          var isAvail = message.extra.available;
          el.printerStatus.className = "status " + (isAvail ? "ok" : "err");
          el.printerStatus.textContent = isAvail
            ? "Siap - " + (message.extra.port || "port") + " (" + (message.extra.driver || "driver") + ")"
            : (message.extra.status || "Tidak terdeteksi");
          if (el.printerBoxDot) el.printerBoxDot.className = "status-dot " + (isAvail ? "ok" : "err");
          if (el.printerStatusDot) el.printerStatusDot.className = "status-indicator-dot " + (isAvail ? "ok" : "err");
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

    if (webview) {
      webview.addEventListener("message", function (event) { handleMessage(event.data || {}); });
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

