(() => {
  const joinBtn = document.getElementById("join-btn");
  const modal = document.getElementById("join-modal");
  const copyBtn = document.getElementById("copy-btn");
  const toast = document.getElementById("toast");
  const addressEl = document.getElementById("server-address");
  if (!joinBtn || !modal || !copyBtn || !toast) return;

  const address =
    joinBtn.dataset.address ||
    (addressEl && addressEl.textContent.trim()) ||
    "play.glyphmc.net";

  let hideTimer;
  let lastFocus = null;

  function showToast() {
    toast.hidden = false;
    requestAnimationFrame(() => toast.classList.add("is-visible"));
    clearTimeout(hideTimer);
    hideTimer = setTimeout(() => {
      toast.classList.remove("is-visible");
      setTimeout(() => {
        toast.hidden = true;
      }, 350);
    }, 2200);
  }

  async function copyAddress() {
    try {
      await navigator.clipboard.writeText(address);
    } catch {
      const field = document.createElement("textarea");
      field.value = address;
      field.setAttribute("readonly", "");
      field.style.position = "fixed";
      field.style.opacity = "0";
      document.body.appendChild(field);
      field.select();
      document.execCommand("copy");
      field.remove();
    }
    copyBtn.textContent = "Copied";
    copyBtn.classList.add("is-copied");
    showToast();
    setTimeout(() => {
      copyBtn.textContent = "Copy";
      copyBtn.classList.remove("is-copied");
    }, 1800);
  }

  function openModal() {
    lastFocus = document.activeElement;
    modal.hidden = false;
    document.body.classList.add("modal-open");
    requestAnimationFrame(() => modal.classList.add("is-open"));
    const closeBtn = modal.querySelector(".modal-close");
    if (closeBtn) closeBtn.focus();
  }

  function closeModal() {
    modal.classList.remove("is-open");
    document.body.classList.remove("modal-open");
    setTimeout(() => {
      modal.hidden = true;
      if (lastFocus && typeof lastFocus.focus === "function") lastFocus.focus();
    }, 220);
  }

  joinBtn.addEventListener("click", openModal);
  copyBtn.addEventListener("click", copyAddress);

  modal.querySelectorAll("[data-close]").forEach((el) => {
    el.addEventListener("click", closeModal);
  });

  document.addEventListener("keydown", (event) => {
    if (event.key === "Escape" && !modal.hidden) {
      closeModal();
    }
  });
})();
