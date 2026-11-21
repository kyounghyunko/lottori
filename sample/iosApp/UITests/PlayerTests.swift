import XCTest

final class PlayerTests: XCTestCase {
    func testSeekAndInvalidFiles() {
        let app = XCUIApplication()
        app.launch()
        func button(_ name: String) -> XCUIElement { app.buttons[name].firstMatch }
        func text(_ name: String) -> XCUIElement {
            app.descendants(matching: .any).matching(NSPredicate(format: "label CONTAINS %@", name)).firstMatch
        }
        func capture(_ name: String) {
            let attachment = XCTAttachment(screenshot: app.screenshot())
            attachment.name = name
            attachment.lifetime = .keepAlways
            add(attachment)
        }
        XCTAssertTrue(button("Pause").waitForExistence(timeout: 15))
        XCTAssertTrue(text("Animation preview").exists)
        button("Moving circle").tap()
        XCTAssertTrue(button("50%").waitForExistence(timeout: 5))
        button("50%").tap()
        XCTAssertTrue(text("Animation preview").exists)
        assertCenteredCircle(in: text("Animation preview").screenshot().image)
        capture("moving-050")
        button("Trim path").tap()
        XCTAssertTrue(text("Static fallback").waitForExistence(timeout: 5))
        XCTAssertTrue(text("TrimPath  $.layers[0].shapes[0].it[1]  checkmark").exists)
        capture("trim-fallback")
        button("Damaged JSON").tap()
        XCTAssertTrue(text("invalid_json").waitForExistence(timeout: 5))
        XCTAssertTrue(text("Static status icon").waitForExistence(timeout: 5))
        button("ZIP").tap()
        XCTAssertTrue(text("UnsupportedContainer").waitForExistence(timeout: 5))
        button("Static circle").tap()
        XCTAssertTrue(text("Animation preview").waitForExistence(timeout: 5))
    }
    private func assertCenteredCircle(in image: UIImage) {
        guard let cg = image.cgImage else { return XCTFail("Missing preview screenshot") }
        let width = cg.width, height = cg.height
        var bytes = [UInt8](repeating: 0, count: width * height * 4)
        let drawn = bytes.withUnsafeMutableBytes { storage -> Bool in
            guard let context = CGContext(data: storage.baseAddress, width: width, height: height,
                bitsPerComponent: 8, bytesPerRow: width * 4, space: CGColorSpaceCreateDeviceRGB(),
                bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return false }
            context.draw(cg, in: CGRect(x: 0, y: 0, width: width, height: height))
            return true
        }
        XCTAssertTrue(drawn)
        var xTotal = 0, yTotal = 0, count = 0
        for y in 0..<height {
            for x in 0..<width {
                let i = 4 * (width * y + x)
                if bytes[i] > 220 && bytes[i + 1] < 30 && bytes[i + 2] < 30 {
                    xTotal += x
                    yTotal += y
                    count += 1
                }
            }
        }
        guard count >= 16 else { return XCTFail("Red circle is missing") }
        XCTAssertEqual(Double(xTotal) / Double(count), Double(width - 1) / 2, accuracy: 2)
        XCTAssertEqual(Double(yTotal) / Double(count), Double(height - 1) / 2, accuracy: 2)
    }
}
