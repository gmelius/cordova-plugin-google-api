import GoogleSignIn

@objc(GooglePlus)
class GooglePlus: CDVPlugin {
    var commandCallback: String?
    
    @objc(login:)
    func login (command: CDVInvokedUrlCommand) {
        self.commandCallback = command.callbackId
        let options: [String: Any] = command.arguments.first as! [String: Any]
        print(options)

        let reversedClientId: String! = self.getReversedClientId()
        if (reversedClientId == nil) {
            self.sendError("Could not find REVERSED_CLIENT_ID url scheme in app .plist")
            return
        }

        /* let scopesStrinf: String = options["scopes"] as! String
         let serverClientId: String = options["webClientId"] as! String
         let offline: BOOL = options["offline"] as! BOOL
         // Initialize sign-in
         GIDSignIn.sharedInstance().handle(
         URL(options["url"] as! String),
         sourceApplication: options[UIApplicationOpenURLOptionsKey.sourceApplication] as? String,
         annotation: options[UIApplicationOpenURLOptionsKey.annotation]
         ) */

        do {
            if let message = try String(
                data: JSONSerialization.data(
                    withJSONObject: [
                        "test": "Hello World !!",
                        "options": options
                    ],
                    options: []
                ),
                encoding: String.Encoding.utf8
                ) {
                self.send(message)
            }
            else {
                self.sendError("Serializing result failed.")
            }
        } catch let error {
            self.sendError(error.localizedDescription)
        }
    }
    // Get the REVERSED_CLIENT_ID
    func getReversedClientId () -> String! {
        if let urlTypes: [Any] = Bundle.main.infoDictionary!["CFBundleURLTypes"] as? [Any] {
            for value in urlTypes {
                let dict: [String: Any] = value as! [String: Any]
                let urlName: String = dict["CFBundleURLName"] as! String
                if (urlName == "REVERSED_CLIENT_ID") {
                    if let urlSchemes: [String] = dict["CFBundleURLSchemes"] as? [String] {
                        return urlSchemes[0]
                    }
                }
            }
        }
        return nil
    }
    
    // Send result
    func send (_ message: String, _ status: CDVCommandStatus = CDVCommandStatus_OK) {
        if let callbackId = self.commandCallback {
            self.commandCallback = nil
            let pluginResult = CDVPluginResult(
                status: status,
                messageAs: message
            )
            self.commandDelegate!.send(
                pluginResult,
                callbackId: callbackId
            )
        }
    }
    // Send error
    func sendError (_ message: String) {
        self.send(message, CDVCommandStatus_ERROR)
    }
}