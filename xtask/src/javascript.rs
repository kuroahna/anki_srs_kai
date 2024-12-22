// Ideally, we would use the include_str!(...) macro but I can't figure out how
// to get crane (nix library) to include js files in the source when
// craneLib.buildDepsOnly is called

pub const HEADER: &str = r#"
const deckOptions = {
    \"deck1\": {
        easeReward: {
            minimumConsecutiveSuccessfulReviewsRequiredForReward: 3,
            baseEaseReward: 0.05,
            stepEaseReward: 0.05,
            minimumEase: 1.30,
            maximumEase: 2.50,
        },
        scheduler: {
            enableFuzz: true,
            maximumInterval: 36500,
            intervalModifier: 1.00,
            calculateHardMultiplier: (currentEaseFactor, currentInterval) => {
                return 0.0;
            },
            calculateGoodMultiplier: (currentEaseFactor, currentInterval) => {
                return currentEaseFactor / Math.pow(currentInterval, 0.054297);
            },
            calculateEasyMultiplier: (currentEaseFactor, currentInterval) => {
                return 0.0;
            },
        },
    },
    \"Global Settings\": {
        easeReward: {
            minimumConsecutiveSuccessfulReviewsRequiredForReward: 3,
            baseEaseReward: 0.05,
            stepEaseReward: 0.05,
            minimumEase: 1.30,
            maximumEase: 2.50,
        },
        scheduler: {
            enableFuzz: true,
            maximumInterval: 36500,
            intervalModifier: 1.00,
            // Approximation of the default FSRS-5 parameters
            // [0.40255, 1.18385, 3.173, 15.69105, 7.1949, 0.5345, 1.4604, 0.0046, 1.54575, 0.1192, 1.01925, 1.9395, 0.11, 0.29605, 2.2698, 0.2315, 2.9898, 0.51655, 0.6621]
            calculateHardMultiplier: (currentEaseFactor, currentInterval) => {
                return currentEaseFactor * Math.pow(currentInterval, -0.013242011) + (-1.048236196);
            },
            calculateGoodMultiplier: (currentEaseFactor, currentInterval) => {
                return currentEaseFactor * Math.pow(currentInterval, -0.154370758) + (1.395807731);
            },
            calculateEasyMultiplier: (currentEaseFactor, currentInterval) => {
                return currentEaseFactor * Math.pow(currentInterval, -0.178728777) + (5.295133129);
            },
        },
    },
};
"#;

pub const FOOTER: &str = r##"
const response = new Response(
    wasmBytes,
    {
        headers: {
            'Content-Type': 'application/wasm'
        }
    }
);

try {
    await wasm_bindgen(response);

    wasm_bindgen.calculateNextCardStates(deckOptions, states);
} catch (e) {
    function openCustomModal(message) {
        const modalContainer = document.createElement('div');
        modalContainer.id = 'custom-modal';

        const modalMessage = document.createElement('p');
        modalMessage.id = 'modal-message';
        modalMessage.innerText = message;
        modalMessage.style.fontFamily = 'Arial';

        const okButton = document.createElement('button');
        okButton.innerText = 'OK';
        okButton.onclick = closeCustomModal;

        modalContainer.appendChild(modalMessage);
        modalContainer.appendChild(okButton);

        modalContainer.style.display = 'none';
        modalContainer.style.position = 'fixed';
        modalContainer.style.top = '50%';
        modalContainer.style.left = '50%';
        modalContainer.style.transform = 'translate(-50%, -50%)';
        modalContainer.style.padding = '20px';
        modalContainer.style.backgroundColor = '#fff';
        modalContainer.style.border = '1px solid #ccc';
        modalContainer.style.boxShadow = '0 2px 10px rgba(0, 0, 0, 0.1)';
        modalContainer.style.zIndex = '1000';

        modalMessage.style.marginBottom = '15px';
        modalMessage.style.color = '#000';

        okButton.style.cursor = 'pointer';
        okButton.style.padding = '8px 16px';
        okButton.style.backgroundColor = '#007BFF';
        okButton.style.color = '#fff';
        okButton.style.border = 'none';
        okButton.style.borderRadius = '4px';

        document.body.appendChild(modalContainer);

        modalContainer.style.display = 'block';
    }

    function closeCustomModal() {
        const modalContainer = document.getElementById('custom-modal');
        document.body.removeChild(modalContainer);
    }

    openCustomModal(e.message);
}
"##;
